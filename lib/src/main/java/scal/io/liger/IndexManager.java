package scal.io.liger;

import timber.log.Timber;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.support.annotation.StringDef;
import android.util.Log;

import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import scal.io.liger.model.BaseIndexItem;
import scal.io.liger.model.ContentPackMetadata;
import scal.io.liger.model.ExpansionIndexItem;
import scal.io.liger.model.InstanceIndexItem;
import scal.io.liger.model.StoryPath;
import scal.io.liger.model.StoryPathLibrary;

/**
 * Created by mnbogner on 11/24/14.
 */
public class IndexManager {

    private static String availableIndexName = "available_index";
    private static String installedIndexName = "installed_index.json";
    private static String instanceIndexName = "instance_index.json";
    private static String contentIndexName = "content_index.json";
    private static String contentMetadataName = "content_metadata.json";

    public static String noPatchFile = "NOPATCH";

    // TODO Temporarily public for debugging convenience
    public static HashMap<String, ArrayList<ExpansionIndexItem>> cachedIndexes = new HashMap<>();

    public static String buildFileAbsolutePath(ExpansionIndexItem item,
                                               @Constants.ObbType String mainOrPatch,
                                               Context context) {

        // Use File constructor to avoid duplicate or missing file path separators after concatenation
        return new File(buildFilePath(item, context) + buildFileName(item, mainOrPatch)).getAbsolutePath();

    }

    public static String buildFileName(ExpansionIndexItem item,
                                       @Constants.ObbType String mainOrPatch) {
        if (Constants.MAIN.equals(mainOrPatch)) {
            return item.getExpansionId() + "." + mainOrPatch + "." + item.getExpansionFileVersion() + ".obb";
        } else if (Constants.PATCH.equals(mainOrPatch)) {
            if (item.getPatchFileVersion() == null) {
                // not really an error, removing message
                // Timber.d("CAN'T CONSTRUCT FILENAME FOR " + item.getExpansionId() + ", PATCH VERSION IS NULL");
                return noPatchFile;
            } else {
                return item.getExpansionId() + "." + mainOrPatch + "." + item.getPatchFileVersion() + ".obb";
            }
        } else {
            Timber.d("CAN'T CONSTRUCT FILENAME FOR " + item.getExpansionId() + ", DON'T UNDERSTAND " + mainOrPatch);
            // this is not the same as having no patch
            // return noPatchFile;
            return "FOO";
        }
    }



    // adding these to smooth transition to storymaker ExpansionIndexItem class

    public static String buildFileAbsolutePath(String expansionId,
                                               String mainOrPatch,
                                               String version,
                                               Context context) {

        String filePath = buildFilePath(context);

        String fileName = buildFileName(expansionId, mainOrPatch, version);

        return new File(filePath, fileName).getAbsolutePath();
    }

    public static String buildFilePath(Context context) {

        return StorageHelper.getActualStorageDirectory(context).getPath();
    }

    public static String buildFileName(String expansionId,
                                       String mainOrPatch,
                                       String version) {

        return expansionId + "." + mainOrPatch + "." + version + ".obb";
    }



    public static String buildFilePath(ExpansionIndexItem item, Context context) {

        // TODO - switching to the new storage method ignores the value set in the expansion index item
        // String checkPath = Environment.getExternalStorageDirectory().toString() + File.separator + item.getExpansionFilePath();
        String checkPath = StorageHelper.getActualStorageDirectory(context).getPath() + File.separator;

        File checkDir = new File(checkPath);
        if (checkDir.isDirectory() || checkDir.mkdirs()) {
            return checkPath;
        } else {
            Timber.d("CAN'T CONSTRUCT PATH FOR " + item.getExpansionId() + ", PATH " + item.getExpansionFilePath() + " DOES NOT EXIST AND COULD NOT BE CREATED");
            return null;
        }
    }

    // only available index should be copied, so collapsing methods

    public static void copyAvailableIndex(Context context, boolean forceCopy) {

        AssetManager assetManager = context.getAssets();

        String jsonFilePath = ZipHelper.getFileFolderName(context);

        Timber.d("COPYING JSON FILE " + availableIndexName + ".json" + " FROM ASSETS TO " + jsonFilePath);

        // only replace file if version is different
        File jsonFile = new File(jsonFilePath + getAvailableVersionName());
        if (jsonFile.exists() && !forceCopy) {
            Timber.d("JSON FILE " + jsonFile.getName() + " ALREADY EXISTS IN " + jsonFilePath + ", NOT COPYING");
            return;
        } else {

            // delete old patch versions
            String nameFilter = availableIndexName + "." + "*" + ".json";

            Timber.d("CLEANUP: DELETING " + nameFilter + " FROM " + jsonFilePath);

            WildcardFileFilter indexFileFilter = new WildcardFileFilter(nameFilter);
            File indexDirectory = new File(jsonFilePath);
            for (File indexFile : FileUtils.listFiles(indexDirectory, indexFileFilter, null)) {
                Timber.d("CLEANUP: FOUND " + indexFile.getPath() + ", DELETING");
                FileUtils.deleteQuietly(indexFile);
            }

            // delete old un-numbered files
            File oldFile = new File(jsonFilePath + availableIndexName + ".json");
            if (oldFile.exists()) {
                Timber.d("CLEANUP: FOUND " + oldFile.getPath() + ", DELETING");
                FileUtils.deleteQuietly(oldFile);
            }
        }

        InputStream assetIn = null;
        OutputStream assetOut = null;

        try {
            assetIn = assetManager.open(availableIndexName + ".json");

            assetOut = new FileOutputStream(jsonFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = assetIn.read(buffer)) != -1) {
                assetOut.write(buffer, 0, read);
            }
            assetIn.close();
            assetIn = null;
            assetOut.flush();
            assetOut.close();
            assetOut = null;
        } catch (IOException ioe) {
            Timber.e("COPYING JSON FILE " + availableIndexName + ".json" + " FROM ASSETS TO " + jsonFilePath + " FAILED");
            return;
        }

        // check for zero-byte files
        if (jsonFile.exists() && (jsonFile.length() == 0)) {
            Timber.e("COPYING JSON FILE " + availableIndexName + ".json" + " FROM ASSETS TO " + jsonFilePath + " FAILED (FILE WAS ZERO BYTES)");
            jsonFile.delete();
        }

        return;
    }

    // need to move this elsewhere
    public static File copyThumbnail(Context context, String thumbnailFileName) {

        AssetManager assetManager = context.getAssets();

        String thumbnailFilePath = ZipHelper.getFileFolderName(context);

        Timber.d("COPYING THUMBNAIL FILE " + thumbnailFileName + " FROM ASSETS TO " + thumbnailFilePath);

        File thumbnailFile = new File(thumbnailFilePath + thumbnailFileName);

        if (thumbnailFile.exists()) {
            Timber.d("THUMBNAIL FILE " + thumbnailFileName + " ALREADY EXISTS IN " + thumbnailFilePath + ", DELETING");
            thumbnailFile.delete();
        }

        File thumbnailDirectory = new File(thumbnailFile.getParent());
        if (!thumbnailDirectory.exists()) {
            thumbnailDirectory.mkdirs();
        }

        InputStream assetIn = null;
        OutputStream assetOut = null;

        try {
            assetIn = assetManager.open(thumbnailFileName);

            assetOut = new FileOutputStream(thumbnailFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = assetIn.read(buffer)) != -1) {
                assetOut.write(buffer, 0, read);
            }
            assetIn.close();
            assetIn = null;
            assetOut.flush();
            assetOut.close();
            assetOut = null;
        } catch (IOException ioe) {
            Timber.e("COPYING THUMBNAIL FILE " + thumbnailFileName + " FROM ASSETS TO " + thumbnailFilePath + " FAILED: " + ioe.getLocalizedMessage());
            return null;
        }

        // check for zero-byte files
        if (thumbnailFile.exists() && (thumbnailFile.length() == 0)) {
            Timber.e("COPYING THUMBNAIL FILE " + thumbnailFileName + " FROM ASSETS TO " + thumbnailFilePath + " FAILED (FILE WAS ZERO BYTES)");
            thumbnailFile.delete();
            return null;
        }

        return thumbnailFile;
    }

    public static HashMap<String, ExpansionIndexItem> loadAvailableFileIndex(Context context) {

        ArrayList<ExpansionIndexItem> indexList = loadIndex(context, getAvailableVersionName());

        HashMap<String, ExpansionIndexItem> indexMap = new HashMap<String, ExpansionIndexItem>();

        for (ExpansionIndexItem item : indexList) {
            // construct name (index by main file names)
            String fileName = buildFileName(item, Constants.MAIN);
            indexMap.put(fileName, item);
        }

        return indexMap;
    }

    public static HashMap<String, ExpansionIndexItem> loadInstalledFileIndex(Context context) {

        ArrayList<ExpansionIndexItem> indexList = loadIndex(context, installedIndexName);

        HashMap<String, ExpansionIndexItem> indexMap = new HashMap<String, ExpansionIndexItem>();

        for (ExpansionIndexItem item : indexList) {
            // construct names (index by main and patch file names)
            String mainName = buildFileName(item, Constants.MAIN);
            indexMap.put(mainName, item);
            String patchName = buildFileName(item, Constants.MAIN);
            indexMap.put(patchName, item);
        }

        return indexMap;
    }

    public static HashMap<String, ExpansionIndexItem> loadAvailableOrderIndex(Context context) {

        ArrayList<ExpansionIndexItem> indexList = loadIndex(context, getAvailableVersionName());

        HashMap<String, ExpansionIndexItem> indexMap = new HashMap<String, ExpansionIndexItem>();

        for (ExpansionIndexItem item : indexList) {
            indexMap.put(item.getPatchOrder(), item);
        }

        return indexMap;
    }

    public static HashMap<String, ExpansionIndexItem> loadInstalledOrderIndex(Context context) {

        ArrayList<ExpansionIndexItem> indexList = loadIndex(context, installedIndexName);

        HashMap<String, ExpansionIndexItem> indexMap = new HashMap<String, ExpansionIndexItem>();

        for (ExpansionIndexItem item : indexList) {
            indexMap.put(item.getPatchOrder(), item);
        }

        return indexMap;
    }

    public static HashMap<String, ExpansionIndexItem> loadAvailableIdIndex(Context context) {

        ArrayList<ExpansionIndexItem> indexList = loadIndex(context, getAvailableVersionName());

        HashMap<String, ExpansionIndexItem> indexMap = new HashMap<String, ExpansionIndexItem>();

        for (ExpansionIndexItem item : indexList) {
            indexMap.put(item.getExpansionId(), item);
        }

        return indexMap;
    }

    public static HashMap<String, ExpansionIndexItem> loadInstalledIdIndex(Context context) {

        ArrayList<ExpansionIndexItem> indexList = loadIndex(context, installedIndexName);

        HashMap<String, ExpansionIndexItem> indexMap = new HashMap<String, ExpansionIndexItem>();

        for (ExpansionIndexItem item : indexList) {
            indexMap.put(item.getExpansionId(), item);
        }

        return indexMap;
    }

    // supressing messages for less text during polling

    /**
     * This method does an unacceptable amount of work for synchronous use from the main thread
     */
    @Deprecated
    private static ArrayList<ExpansionIndexItem> loadIndex(Context context, String jsonFileName) {
        long startTime = System.currentTimeMillis();

        ArrayList<ExpansionIndexItem> indexList = null;

        if (!cachedIndexes.containsKey(jsonFileName)) {
            String indexJson = null;
            indexList = new ArrayList<ExpansionIndexItem>();

            String jsonFilePath = ZipHelper.getFileFolderName(context);

            // Timber.d("READING JSON FILE " + jsonFilePath + jsonFileName + " FROM SD CARD");

            File jsonFile = new File(jsonFilePath + jsonFileName);
            if (!jsonFile.exists()) {
                Timber.e(jsonFilePath + jsonFileName + " WAS NOT FOUND");
                return indexList;
            }

            String sdCardState = Environment.getExternalStorageState();

            if (sdCardState.equals(Environment.MEDIA_MOUNTED)) {
                try {
                    InputStream jsonStream = new FileInputStream(jsonFile);

                    int size = jsonStream.available();
                    byte[] buffer = new byte[size];
                    jsonStream.read(buffer);
                    jsonStream.close();
                    jsonStream = null;
                    indexJson = new String(buffer);
                } catch (IOException ioe) {
                    Timber.e("READING JSON FILE " + jsonFilePath + jsonFileName + " FROM SD CARD FAILED");
                    return indexList;
                }
            } else {
                Timber.e("SD CARD WAS NOT FOUND");
                return indexList;
            }

            if ((indexJson.length() > 0)) {
                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                indexList = gson.fromJson(indexJson, new TypeToken<ArrayList<ExpansionIndexItem>>() {
                }.getType());

                cachedIndexes.put(jsonFileName, indexList);
            }
        } else {
            indexList = cachedIndexes.get(jsonFileName);
        }

        //Timber.d(String.format("%d index items loaded for %s in %d ms", indexList.size(), jsonFileName, System.currentTimeMillis() - startTime));
        return indexList;
    }

    // only one key option for instance index
    public static HashMap<String, InstanceIndexItem> loadInstanceIndex(Context context) {

        HashMap<String, InstanceIndexItem> indexMap = new HashMap<String, InstanceIndexItem>();

        String indexJson = null;
        ArrayList<InstanceIndexItem> indexList = new ArrayList<InstanceIndexItem>();

        String jsonFilePath = ZipHelper.getFileFolderName(context);

        Timber.d("READING JSON FILE " + jsonFilePath + instanceIndexName + " FROM SD CARD");

        File jsonFile = new File(jsonFilePath + instanceIndexName);
        if (!jsonFile.exists()) {
            Timber.d(jsonFilePath + instanceIndexName + " WAS NOT FOUND");
        } else {

            String sdCardState = Environment.getExternalStorageState();

            if (sdCardState.equals(Environment.MEDIA_MOUNTED)) {
                try {
                    InputStream jsonStream = new FileInputStream(jsonFile);

                    int size = jsonStream.available();
                    byte[] buffer = new byte[size];
                    jsonStream.read(buffer);
                    jsonStream.close();
                    jsonStream = null;
                    indexJson = new String(buffer);
                } catch (IOException ioe) {
                    Timber.e("READING JSON FILE " + jsonFilePath + instanceIndexName + " FROM SD CARD FAILED");
                }
            } else {
                Timber.e("SD CARD WAS NOT FOUND");
                return indexMap; // if there's no card, there's nowhere to read instance files from, so just stop here
            }

            if ((indexJson != null) && (indexJson.length() > 0)) {
                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                try {
                    indexList = gson.fromJson(indexJson, new TypeToken<ArrayList<InstanceIndexItem>>() {
                    }.getType());
                } catch (Exception e) {
                    Timber.e(indexJson);
                    throw e;
                }
            }

            for (InstanceIndexItem item : indexList) {
                indexMap.put(item.getInstanceFilePath(), item);
            }
        }

        return indexMap;
    }

    // only one key option for instance index
    public static ArrayList<InstanceIndexItem> loadInstanceIndexAsList(Context context) {

        String indexJson = null;
        ArrayList<InstanceIndexItem> indexList = new ArrayList<InstanceIndexItem>();

        String jsonFilePath = ZipHelper.getFileFolderName(context);

        Timber.d("READING JSON FILE " + jsonFilePath + instanceIndexName + " FROM SD CARD");

        File jsonFile = new File(jsonFilePath + instanceIndexName);
        if (!jsonFile.exists()) {
            Timber.d(jsonFilePath + instanceIndexName + " WAS NOT FOUND");
        } else {

            String sdCardState = Environment.getExternalStorageState();

            if (sdCardState.equals(Environment.MEDIA_MOUNTED)) {
                try {
                    InputStream jsonStream = new FileInputStream(jsonFile);

                    int size = jsonStream.available();
                    byte[] buffer = new byte[size];
                    jsonStream.read(buffer);
                    jsonStream.close();
                    jsonStream = null;
                    indexJson = new String(buffer);
                } catch (IOException ioe) {
                    // FIXME we need to centralize the path finding logic so we can have a single place with sensible degredation (fallback to internal if there's no SD, deal well with SD beign removed temporarily)
                    Timber.e("READING JSON FILE " + jsonFilePath + instanceIndexName + " FROM SD CARD FAILED");
                }
            } else {
                Timber.e("SD CARD WAS NOT FOUND");
                return indexList; // if there's no card, there's nowhere to read instance files from, so just stop here
            }

            if ((indexJson != null) && (indexJson.length() > 0)) {
                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                indexList = gson.fromJson(indexJson, new TypeToken<ArrayList<InstanceIndexItem>>() {
                }.getType());
            }
        }

        return indexList;
    }

    // only one key option for content index, file is loaded from a zipped content pack
    // content index is read only, no register/update/save methods
    // TODO this should leverage the loadContentIndexAsList to avoid dupliction
    public static HashMap<String, InstanceIndexItem> loadContentIndex(Context context, String packageName, String expansionId, String language) {

        String contentJson = null;
        ArrayList<InstanceIndexItem> contentList = new ArrayList<InstanceIndexItem>();
        HashMap<String, InstanceIndexItem> contentMap = new HashMap<String, InstanceIndexItem>();

        String contentPath = packageName + File.separator + expansionId + File.separator + contentIndexName;

        Timber.d("READING JSON FILE " + contentPath + " FROM ZIP FILE");

        try {
            InputStream jsonStream = ZipHelper.getFileInputStream(contentPath, context, language);

            if (jsonStream == null) {
                Timber.e("READING JSON FILE " + contentPath + " FROM ZIP FILE FAILED (STREAM WAS NULL)");
                return contentMap;
            }

            int size = jsonStream.available();
            byte[] buffer = new byte[size];
            jsonStream.read(buffer);
            jsonStream.close();
            contentJson = new String(buffer);

            if ((contentJson.length() > 0)) {
                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                contentList = gson.fromJson(contentJson, new TypeToken<ArrayList<InstanceIndexItem>>() {
                }.getType());
            }

            for (InstanceIndexItem item : contentList) {
                contentMap.put(item.getInstanceFilePath(), item);
            }
        } catch (IOException ioe) {
            Timber.e("READING JSON FILE " + contentPath + " FROM ZIP FILE FAILED: " + ioe.getMessage());
            return contentMap;
        }

        return contentMap;
    }


    // only one key option for content index, file is loaded from a zipped content pack
    // content index is read only, no register/update/save methods
    public static ArrayList<InstanceIndexItem> loadContentIndexAsList(Context context, String packageName, String expansionId, String language) {

        String contentJson = null;
        ArrayList<InstanceIndexItem> contentList = new ArrayList<InstanceIndexItem>();

        String contentPath = packageName + File.separator + expansionId + File.separator + contentIndexName;

        Timber.d("READING JSON FILE " + contentPath + " FROM ZIP FILE");

        try {
            InputStream jsonStream = ZipHelper.getFileInputStream(contentPath, context, language);

            if (jsonStream == null) {
                Timber.e("READING JSON FILE " + contentPath + " FROM ZIP FILE FAILED (STREAM WAS NULL)");
                return contentList;
            }

            int size = jsonStream.available();
            byte[] buffer = new byte[size];
            jsonStream.read(buffer);
            jsonStream.close();
            contentJson = new String(buffer);

            if ((contentJson.length() > 0)) {
                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                contentList = gson.fromJson(contentJson, new TypeToken<ArrayList<InstanceIndexItem>>() {
                }.getType());
            }
        } catch (IOException ioe) {
            Timber.e("READING JSON FILE " + contentPath + " FROM ZIP FILE FAILED: " + ioe.getMessage());
        }

        return contentList;
    }

    // not strictly an index, but including here because code is similar
    public static ContentPackMetadata loadContentMetadata(Context context, String packageName, String expansionId, String language) {

        String metadataJson = null;
        ContentPackMetadata metadata = null;

        String metadataPath = packageName + File.separator + expansionId + File.separator + contentMetadataName;

        Timber.d("READING JSON FILE " + metadataPath + " FROM ZIP FILE");

        try {
            InputStream jsonStream = ZipHelper.getFileInputStream(metadataPath, context, language);

            if (jsonStream == null) {
                Timber.e("READING JSON FILE " + metadataPath + " FROM ZIP FILE FAILED (STREAM WAS NULL)");
                return null;
            }

            int size = jsonStream.available();
            byte[] buffer = new byte[size];
            jsonStream.read(buffer);
            jsonStream.close();
            metadataJson = new String(buffer);

            if ((metadataJson.length() > 0)) {
                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                metadata = gson.fromJson(metadataJson, new TypeToken<ContentPackMetadata>() {
                }.getType());
            }
        } catch (IOException ioe) {
            Timber.e("READING JSON FILE " + metadataPath + " FROM ZIP FILE FAILED: " + ioe.getMessage());
            return null;
        }

        return metadata;
    }


    public static HashMap<String, String> loadTempateIndex (Context context) {
        HashMap<String, String> templateMap = new HashMap<String, String>();

        ZipResourceFile zrf = ZipHelper.getResourceFile(context);
        ArrayList<ZipResourceFile.ZipEntryRO> zipEntries = new ArrayList<ZipResourceFile.ZipEntryRO>(Arrays.asList(zrf.getAllEntries()));
        for (ZipResourceFile.ZipEntryRO zipEntry : zipEntries) {
            // Timber.d("GOT ITEM: " + zipEntry.mFileName);
            templateMap.put(zipEntry.mFileName.substring(zipEntry.mFileName.lastIndexOf(File.separator) + 1), zipEntry.mFileName);
        }

        return templateMap;
    }

    public static HashMap<String, InstanceIndexItem> fillInstanceIndex(Context context, HashMap<String, InstanceIndexItem> indexList, String language) {

        ArrayList<File> instanceFiles = JsonHelper.getLibraryInstanceFiles(context);

        boolean forceSave = false; // need to resolve issue of unset language in existing record preventing update to index

        int initialSize = indexList.size();

        // make a pass to remove deleted files from the index

        ArrayList<String> keys = new ArrayList<String>();

        for (String key : indexList.keySet()) {
            InstanceIndexItem item = indexList.get(key);
            File checkFile = new File(item.getInstanceFilePath());
            if (!checkFile.exists()) {
                Timber.d("REMOVING INDEX ITEM FOR MISSING INSTANCE FILE " + item.getInstanceFilePath());
                keys.add(key);
            }
        }

        for (String key: keys) {
            indexList.remove(key);
        }

        // check for changes
        if (indexList.size() != initialSize) {
            Timber.d(Math.abs(indexList.size() - initialSize) + " ITEMS REMOVED FROM INSTANCE INDEX, FORCING SAVE");
            // update flag
            forceSave = true;
            // update initial size
            initialSize = indexList.size();
        }

        // make a pass to add non-indexed files

        for (final File f : instanceFiles) {
            if (indexList.containsKey(f.getAbsolutePath()) && language.equals(indexList.get(f.getAbsolutePath()).getLanguage())) {
                Timber.d("FOUND INDEX ITEM FOR INSTANCE FILE " + f.getAbsolutePath());
            } else {
                Timber.d("ADDING INDEX ITEM FOR INSTANCE FILE " + f.getAbsolutePath());

                forceSave = true;

                String[] parts = FilenameUtils.removeExtension(f.getName()).split("-");
                String datePart = parts[parts.length - 1]; // FIXME make more robust
                Date date = new Date(Long.parseLong(datePart));

                InstanceIndexItem newItem = new InstanceIndexItem(f.getAbsolutePath(), date.getTime());

                String jsonString = JsonHelper.loadJSON(f.getPath(), context, "en"); // FIXME don't hardcode "en"

                // if no string was loaded, cannot continue
                if (jsonString == null) {
                    Timber.e("json could not be loaded from " + f.getPath());
                    // handle the same way as null spl case below
                    return indexList;
                }

                ArrayList<String> referencedFiles = new ArrayList<String>(); // should not need to insert dependencies to check metadata
                StoryPathLibrary spl = JsonHelper.deserializeStoryPathLibrary(jsonString, f.getAbsolutePath(), referencedFiles, context, language);

                if (spl == null) {
                    return indexList;
                }

                // set language
                newItem.setLanguage(language);

                // first check local metadata fields
                newItem.setTitle(spl.getMetaTitle());
                newItem.setStoryType(spl.getMetaDescription()); // this seems more useful than medium
                newItem.setThumbnailPath(spl.getMetaThumbnail());

                // unsure where to put additional fields

                // if anything is missing, open story path
                if ((newItem.getTitle() == null) ||
                    (newItem.getStoryType() == null) ||
                    (newItem.getThumbnailPath() == null)) {
                    Timber.d("MISSING METADATA, OPENING STORY PATH FOR INSTANCE FILE " + f.getAbsolutePath());

                    if (spl.getCurrentStoryPathFile() != null) {
                        spl.loadStoryPathTemplate("CURRENT", false);
                    }

                    StoryPath currentStoryPath = spl.getCurrentStoryPath();

                    if (currentStoryPath != null) {
                        // null values will be handled by the index card builder
                        if (newItem.getTitle() == null) {
                            newItem.setTitle(currentStoryPath.getTitle());
                        }
                        if (newItem.getStoryType() == null) {
                            newItem.setStoryType(currentStoryPath.getMedium());
                        }
                        if (newItem.getThumbnailPath() == null) {
                            newItem.setThumbnailPath(spl.getMetaThumbnail());
                        }
                    }
                } else {
                    Timber.d("METADATA COMPLETE FOR INSTANCE FILE " + f.getAbsolutePath());
                }

                indexList.put(newItem.getInstanceFilePath(), newItem);
            }
        }

        // check for changes again
        if (indexList.size() != initialSize) {
            Timber.d(Math.abs(indexList.size() - initialSize) + " ITEMS ADDED TO INSTANCE INDEX, FORCING SAVE");
            // update flag
            forceSave = true;
            // update initial size
            initialSize = indexList.size();
        }

        // persist updated index (if necessary)
        if (forceSave) {
            ArrayList<InstanceIndexItem> indexArray = new ArrayList<InstanceIndexItem>(indexList.values());
            saveInstanceIndex(context, indexArray, instanceIndexName);
        } else {
            Timber.d("NOTHING ADDED TO/REMOVED FROM INSTANCE INDEX, NO SAVE");
        }

        return indexList;
    }

    // unused
    /*
    public static void registerAvailableIndexItem(Context context, ExpansionIndexItem indexItem) {

        HashMap<String, ExpansionIndexItem> indexMap = loadAvailableIdIndex(context);
        indexMap.put(indexItem.getExpansionId(), indexItem);
        ArrayList<ExpansionIndexItem> indexList = new ArrayList<ExpansionIndexItem>();
        for (ExpansionIndexItem eii : indexMap.values()) {
            indexList.add(eii);
        }
        saveIndex(context, indexList, getAvailableVersionName());
        return;
    }
    */

    public static void registerInstalledIndexItem(Context context, ExpansionIndexItem indexItem) {

        HashMap<String, ExpansionIndexItem> indexMap = loadInstalledIdIndex(context);
        indexMap.put(indexItem.getExpansionId(), indexItem);
        ArrayList<ExpansionIndexItem> indexList = new ArrayList<ExpansionIndexItem>();
        for (ExpansionIndexItem eii : indexMap.values()) {
            indexList.add(eii);
        }
        saveIndex(context, indexList, installedIndexName);
        return;
    }

    // unused
    /*
    public static void unregisterAvailableIndexItem(Context context, ExpansionIndexItem indexItem) {

        HashMap<String, ExpansionIndexItem> indexMap = loadAvailableIdIndex(context);
        indexMap.remove(indexItem.getExpansionId());
        ArrayList<ExpansionIndexItem> indexList = new ArrayList<ExpansionIndexItem>();
        for (ExpansionIndexItem eii : indexMap.values()) {
            indexList.add(eii);
        }
        saveIndex(context, indexList, getAvailableVersionName());
        return;
    }

    public static void unregisterInstalledIndexItem(Context context, ExpansionIndexItem indexItem) {

        HashMap<String, ExpansionIndexItem> indexMap = loadInstalledIdIndex(context);
        indexMap.remove(indexItem.getExpansionId());
        ArrayList<ExpansionIndexItem> indexList = new ArrayList<ExpansionIndexItem>();
        for (ExpansionIndexItem eii : indexMap.values()) {
            indexList.add(eii);
        }
        saveIndex(context, indexList, installedIndexName);
        return;
    }
    */

    // unused
    /*
    public static void unregisterAvailableIndexItem(Context context, String fileName) {

        HashMap<String, ExpansionIndexItem> indexMap = loadAvailableFileIndex(context);
        indexMap.remove(fileName);
        ArrayList<ExpansionIndexItem> indexList = new ArrayList<ExpansionIndexItem>();
        for (ExpansionIndexItem eii : indexMap.values()) {
            indexList.add(eii);
        }
        saveIndex(context, indexList, getAvailableVersionName());
        return;
    }

    public static void unregisterInstalledIndexItem(Context context, String fileName) {

        HashMap<String, ExpansionIndexItem> indexMap = loadInstalledFileIndex(context);
        indexMap.remove(fileName);
        ArrayList<ExpansionIndexItem> indexList = new ArrayList<ExpansionIndexItem>();
        for (ExpansionIndexItem eii : indexMap.values()) {
            indexList.add(eii);
        }
        saveIndex(context, indexList, installedIndexName);
        return;
    }
    */

    public static void saveAvailableIndex(Context context, HashMap<String, ExpansionIndexItem> indexMap) {

        saveIndex(context, new ArrayList(indexMap.values()), getAvailableVersionName());

        return;
    }

    public static void saveInstalledIndex(Context context, HashMap<String, ExpansionIndexItem> indexMap) {

        saveIndex(context, new ArrayList(indexMap.values()), installedIndexName);

        return;
    }

    private static void saveIndex(Context context, ArrayList<ExpansionIndexItem> indexList, String jsonFileName) {

        String indexJson = "";

        String jsonFilePath = ZipHelper.getFileFolderName(context);

        // need to update cached index
        if (cachedIndexes.containsKey(jsonFileName)) {
            cachedIndexes.put(jsonFileName, indexList);
        }

        // need to purge ZipHelper cache to force update
        ZipHelper.clearCache();

        Timber.d("WRITING JSON FILE " + jsonFilePath + jsonFileName + " TO SD CARD");

        File jsonFile = new File(jsonFilePath + jsonFileName + ".tmp"); // write to temp and rename
        if (jsonFile.exists()) {
            jsonFile.delete();
        }

        String sdCardState = Environment.getExternalStorageState();

        if (sdCardState.equals(Environment.MEDIA_MOUNTED)) {
            try {
                jsonFile.createNewFile();

                FileOutputStream jsonStream = new FileOutputStream(jsonFile);

                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                indexJson = gson.toJson(indexList);

                byte[] buffer = indexJson.getBytes();
                jsonStream.write(buffer);
                jsonStream.flush();
                jsonStream.close();
                jsonStream = null;

                Process p = Runtime.getRuntime().exec("mv " + jsonFilePath + jsonFileName + ".tmp " + jsonFilePath + jsonFileName);

            } catch (IOException ioe) {
                Timber.e("WRITING JSON FILE " + jsonFilePath + jsonFileName + " TO SD CARD FAILED");
                return;
            }
        } else {
            Timber.e("SD CARD WAS NOT FOUND");
            return;
        }
    }

    public static void instanceIndexAdd(Context context, InstanceIndexItem addItem, HashMap<String, InstanceIndexItem> indexList) {

        indexList.put(addItem.getInstanceFilePath(), addItem);

        ArrayList<InstanceIndexItem> indexArray = new ArrayList<InstanceIndexItem>(indexList.values());

        saveInstanceIndex(context, indexArray, instanceIndexName);

    }

    public static void instanceIndexRemove(Context context, InstanceIndexItem removeItem, HashMap<String, InstanceIndexItem> indexList, boolean deleteFiles, boolean deleteMedia) {

        indexList.remove(removeItem.getInstanceFilePath());

        ArrayList<InstanceIndexItem> indexArray = new ArrayList<InstanceIndexItem>(indexList.values());

        saveInstanceIndex(context, indexArray, instanceIndexName);

        if (deleteFiles) {
            removeItem.deleteAssociatedFiles(context, deleteMedia);
        }
    }

    public static void saveInstanceIndex(Context context, ArrayList<InstanceIndexItem> indexList, String jsonFileName) {

        String indexJson = "";

        String jsonFilePath = ZipHelper.getFileFolderName(context);

        Timber.d("WRITING JSON FILE " + jsonFilePath + jsonFileName + " TO SD CARD");

        File jsonFile = new File(jsonFilePath + jsonFileName + ".tmp"); // write to temp and rename
        if (jsonFile.exists()) {
            jsonFile.delete();
        }

        String sdCardState = Environment.getExternalStorageState();

        if (sdCardState.equals(Environment.MEDIA_MOUNTED)) {
            try {
                jsonFile.createNewFile();

                FileOutputStream jsonStream = new FileOutputStream(jsonFile);

                GsonBuilder gBuild = new GsonBuilder();
                Gson gson = gBuild.create();

                indexJson = gson.toJson(indexList);

                byte[] buffer = indexJson.getBytes();
                jsonStream.write(buffer);
                jsonStream.flush();
                jsonStream.close();
                jsonStream = null;

                Process p = Runtime.getRuntime().exec("mv " + jsonFilePath + jsonFileName + ".tmp " + jsonFilePath + jsonFileName);

            } catch (IOException ioe) {
                Timber.e("WRITING JSON FILE " + jsonFilePath + jsonFileName + " TO SD CARD FAILED");
                return;
            }
        } else {
            Timber.e("SD CARD WAS NOT FOUND");
            return;
        }
    }

    public static String getAvailableVersionName() {
        return availableIndexName + "." + Constants.AVAILABLE_INDEX_VERSION + ".json";
    }
}
