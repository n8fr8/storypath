package scal.io.liger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.stream.MalformedJsonException;
import com.twotoasters.android.support.v7.widget.LinearLayoutManager;
import com.twotoasters.android.support.v7.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

import scal.io.liger.adapter.CardAdapter;
import scal.io.liger.model.Card;
import scal.io.liger.model.ClipCard;
import scal.io.liger.model.Dependency;
import scal.io.liger.model.MediaFile;
import scal.io.liger.model.StoryPath;
import scal.io.liger.model.StoryPathLibrary;


public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    RecyclerView mRecyclerView;
    //CardUI mCardView;
    StoryPathLibrary mStoryPathLibrary;
    //Story mStory;
    CardAdapter mCardAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

////        if (DEVELOPER_MODE) {
//            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
//                    .detectDiskReads()
//                    .detectDiskWrites()
//                    .detectNetwork()   // or .detectAll() for all detectable problems
//                    .penaltyLog()
//                    .build());
//            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
//                    .detectLeakedSqlLiteObjects()
//                    .detectLeakedClosableObjects()
//                    .penaltyLog()
//                    .penaltyDeath()
//                    .build());
////        }

        Log.d("MainActivity", "onCreate");
//        initApp();
        if (savedInstanceState == null) {
            Log.d(TAG, "onSaveInstanceState called with savedInstanceState");
            initApp();
        } else {
            Log.d(TAG, "onSaveInstanceState called with no saved state");
            Log.d("MainActivity", "savedInstanceState not null, check for and load storypath json");
            if (savedInstanceState.containsKey("storyPathJson")) {

                //String json1 = savedInstanceState.getString("storyJson");
                //initStory(json1);

                String json2 = savedInstanceState.getString("storyPathLibraryJson");
                initHook(json2);
                // maybe just initStoryPathLibraryModel?

                String json3 = savedInstanceState.getString("storyPathJson");
                initCardList(json3);
            }
        }
    }

    public void activateCard(Card card) {
        mCardAdapter.addCardAtPosition(card, findSpot(card));
    }

    public void inactivateCard(Card card) {
        mCardAdapter.removeCard(card);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState called");

        if (mStoryPathLibrary == null) {
            Log.d(TAG, "data not yet loaded, no state to save");
        } else {
            //Gson gson = new Gson();
            //mStoryPathLibrary.getCurrentStoryPath().setStoryPathLibraryReference(null);
            //mStoryPathLibrary.getCurrentStoryPath().clearObservers();
            //mStoryPathLibrary.getCurrentStoryPath().clearCardReferences(); // FIXME move this stuff into the model itself so we dont have to worry about it
            //mStoryPathLibrary.getCurrentStoryPath().setContext(null);

            // need to serialize Story as well?

            //String json = gson.toJson(mStoryPathLibrary.getCurrentStoryPath());
            //outState.putString("storyPathJson", json);

            outState.putString("storyPathJson", JsonHelper.serializeStoryPath(mStoryPathLibrary.getCurrentStoryPath()));

            //StoryPath sp = mStoryPathLibrary.getCurrentStoryPath();
            //mStoryPathLibrary.setCurrentStoryPath(null);
            //mStory.setStoryPathLibrary(null);

            //String json2 = gson.toJson(mStory);
            //outState.putString("storyJson", json2);

            //String json3 = gson.toJson(mStoryPathLibrary);
            //outState.putString("storyPathLibraryJson", json3);
            outState.putString("storyPathLibraryJson", JsonHelper.serializeStoryPathLibrary(mStoryPathLibrary));

            //mStory.setStoryPathLibrary(mStoryPathLibrary);
            //mStoryPathLibrary.setCurrentStoryPath(sp);

            //mStoryPathLibrary.getCurrentStoryPath().setContext(this);
            //mStoryPathLibrary.getCurrentStoryPath().setCardReferences();
            //mStoryPathLibrary.getCurrentStoryPath().initializeObservers();
            //mStoryPathLibrary.getCurrentStoryPath().setStoryPathLibraryReference(mStoryPathLibrary);

        }

        super.onSaveInstanceState(outState);
    }

    private void initApp() {
        SharedPreferences sp = getSharedPreferences("appPrefs", Context.MODE_PRIVATE);

        /*
        boolean isFirstStart = sp.getBoolean("isFirstStartFlag", true);

        // if it was the first app start
        if(isFirstStart) {
            // save our flag
            SharedPreferences.Editor e = sp.edit();
            e.putBoolean("isFirstStartFlag", false);
            e.commit();
        }
        */

        JsonHelper.setupFileStructure(this);
        MediaHelper.setupFileStructure(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] jsonFiles = JsonHelper.getJSONFileList();

        //should never happen
        if(jsonFiles.length == 0) {
            jsonFiles = new String[1];
            jsonFiles[0] = "Please add JSON files to the 'Liger' Folder and restart app\n(Located on root of SD card)";

            builder.setTitle("No JSON files found")
                .setItems(jsonFiles, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int index) {
                    }
                });
        }
        else {
            builder.setTitle("Choose Story File(SdCard/Liger/)").setItems(jsonFiles, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int index) {
                    File jsonFile = JsonHelper.setSelectedJSONFile(index);

                    // TEMP - unsure how to best determine new story vs. existing story

                    String json = JsonHelper.loadJSON();

                    initHook(json, jsonFile);

                    // need to implement selection of story path based on hook

                    /*
                    jsonFile = new File(mStoryPathLibrary.buildPath(mStoryPathLibrary.getStoryPathTemplateFiles().get("NAME_1")));
                    json = JsonHelper.loadJSONFromPath(jsonFile.getPath());

                    initCardList(json, jsonFile);
                    */

//                            mStoryPathLibrary.loadStoryPathTemplate("NAME_1");

                            if ((mStoryPathLibrary != null) && (mStoryPathLibrary.getCurrentStoryPathFile() != null)) {
                                mStoryPathLibrary.loadStoryPathTemplate("CURRENT");
                            }

                }
            });
        }

        AlertDialog alert = builder.create();
        alert.show();
    }

    // FIXME rename this to init initial cards or something
    private void initHook(String json) {
        initHook(json, null);
    }

    private void initHook(String json, File jsonFile) {
        Log.d(TAG, "initHook called");

        // unsure what needs to be set up for the hook interface

        try {
            initStoryPathLibraryModel(json, jsonFile);
            setupCardView();
        } catch (com.google.gson.JsonSyntaxException e) {
            Toast.makeText(MainActivity.this, "JSON syntax error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (MalformedJsonException e) {
            Toast.makeText(MainActivity.this, "JSON parsing error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /*
    private void initStory(String json) {
        Log.d(TAG, "initStory called");
        GsonBuilder gBuild = new GsonBuilder();
        Gson gson = gBuild.create();

        try {
            mStory = gson.fromJson(json, Story.class);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "JSON parsing error: " + e.getMessage().substring(e.getMessage().indexOf(":") + 2), Toast.LENGTH_LONG).show();
        }
    }
    */

    private void initStoryPathLibraryModel(String json, File jsonFile) throws MalformedJsonException {
        Log.d(TAG, "initStoryPathLibraryModel called");

        if (jsonFile != null) {
            mStoryPathLibrary = JsonHelper.deserializeStoryPathLibrary(json, jsonFile.getPath(), this);
        } else {
            mStoryPathLibrary = JsonHelper.deserializeStoryPathLibrary(json, null, this);
        }

        /*
        GsonBuilder gBuild = new GsonBuilder();
        Gson gson = gBuild.create();

        mStoryPathLibrary = gson.fromJson(json, StoryPathLibrary.class);

        // a story path library model must have a file location to manage relative paths
        // if it is loaded from a saved state, the location should already be set
        if ((jsonFile == null) || (jsonFile.length() == 0)) {
            if ((mStoryPathLibrary.getFileLocation() == null) || (mStoryPathLibrary.getFileLocation().length() == 0)) {
                Log.e(TAG, "file location for story path library " + mStoryPathLibrary.getId() + " could not be determined");
            }
        } else {
            mStoryPathLibrary.setFileLocation(jsonFile.getPath());
        }

        //if (mStory == null) {
        //    mStory = new Story(mStoryPathLibrary);
        //}
        */
    }

    private void initCardList(String json) {
        initCardList(json, null);
    }

    public void initCardList(String json, File jsonFile) {
        Log.d(TAG, "initCardList called");
        if (mRecyclerView == null)
            return;

        //mCardView.setSwipeable(false);

        try {
            initStoryPathModel(json, jsonFile);
            setupCardView();
        } catch (com.google.gson.JsonSyntaxException e) {
            Toast.makeText(MainActivity.this, "JSON syntax error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (MalformedJsonException e) {
            Toast.makeText(MainActivity.this, "JSON parsing error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshCardList(String json) {
        refreshCardList(json, null);
    }

    public void refreshCardList(String json, File jsonFile) {
        Log.d(TAG, "refreshCardList called");
        if (mRecyclerView == null)
            return;

        //mCardView.setSwipeable(false);

        try {
            initStoryPathModel(json, jsonFile);
            refreshCardViewXXX();
        } catch (com.google.gson.JsonSyntaxException e) {
            Toast.makeText(MainActivity.this, "JSON syntax error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (MalformedJsonException e) {
            Toast.makeText(MainActivity.this, "JSON parsing error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initStoryPathModel(String json, File jsonFile) throws MalformedJsonException {
        Log.d(TAG, "initStoryPathModel called");

        if (jsonFile != null) {
            mStoryPathLibrary.setCurrentStoryPath(JsonHelper.deserializeStoryPath(json, jsonFile.getPath(), mStoryPathLibrary, this));
        } else {
            mStoryPathLibrary.setCurrentStoryPath(JsonHelper.deserializeStoryPath(json, null, mStoryPathLibrary, this));
        }

        /*
        GsonBuilder gBuild = new GsonBuilder();
        gBuild.registerTypeAdapter(StoryPath.class, new StoryPathDeserializer());
        Gson gson = gBuild.create();

        StoryPath sp = gson.fromJson(json, StoryPath.class);
        sp.setContext(this);
        sp.setCardReferences();
        sp.initializeObservers();

        // a story path model must have a file location to manage relative paths
        // if it is loaded from a saved state, the location should already be set
        if ((jsonFile == null) || (jsonFile.length() == 0)) {
            if ((sp.getFileLocation() == null) || (sp.getFileLocation().length() == 0)) {
                Log.e(TAG, "file location for story path " + sp.getId() + " could not be determined");
            }
        } else {
            sp.setFileLocation(jsonFile.getPath());
        }

        sp.setStoryPathLibraryReference(mStoryPathLibrary);


        mStoryPathLibrary.setCurrentStoryPath(sp);
        */
    }

    public void setupCardView () {
        Log.d(TAG, "setupCardView called");
        if (mRecyclerView == null)
            return;

        if (mCardAdapter == null) {

            //add valid cards to view
            ArrayList<Card> cards = new ArrayList<Card>();

            if (mStoryPathLibrary != null) {
//                cards.addAll(mStoryPathLibrary.getValidCards());
                cards = mStoryPathLibrary.getValidCards();
                StoryPath storyPath = mStoryPathLibrary.getCurrentStoryPath();
                if (storyPath != null) {
                    cards.addAll(storyPath.getValidCards());
                    //storyPath.setValidCards(cards);
                }
            }
            mCardAdapter = new CardAdapter(this, cards);
            mRecyclerView.setAdapter(mCardAdapter);
        }

//        if (mCardAdapter == null) {
//
//            //add valid cards to view
//            ArrayList<Card> cards = new ArrayList<Card>();
//
//            if (mStoryPathLibrary != null) {
//                cards.addAll(mStoryPathLibrary.getValidCards());
//
//                if (mStoryPathLibrary.getCurrentStoryPath() != null) {
//                    cards.addAll(mStoryPathLibrary.getCurrentStoryPath().getValidCards());
//                }
//            }
//            mCardAdapter = new CardAdapter(this, cards);
//            mRecyclerView.setAdapter(mCardAdapter);
//        }
    }

    public void refreshCardViewXXX () {
        Log.d(TAG, "refreshCardViewXXX called");
        if (mRecyclerView == null) {
            return;
        }

        if (mCardAdapter == null) {
            setupCardView();
            return;
        }

        //add valid cards to view
        ArrayList<Card> cards = new ArrayList<Card>();

        if (mStoryPathLibrary != null) {
//                cards.addAll(mStoryPathLibrary.getValidCards());
            cards = mStoryPathLibrary.getValidCards();
            StoryPath storyPath = mStoryPathLibrary.getCurrentStoryPath();
            if (storyPath != null) {
                cards.addAll(storyPath.getValidCards());
                //storyPath.setValidCards(cards);
            }
        }
        mCardAdapter = new CardAdapter(this, cards);
        mRecyclerView.setAdapter(mCardAdapter);
    }

    public void goToCard(String cardPath) throws MalformedJsonException {
        Log.d(TAG, "goToCard: " + cardPath);
        // assumes the format story::card::field::value
        String[] pathParts = cardPath.split("::");

        StoryPathLibrary storyPathLibrary = null;
        StoryPath storyPath = null;
        boolean newStoryPath = false;

        /*
        // TEMP CODE FOR TESTING
        if (cardPath == "SWITCH") {
            GsonBuilder gBuild = new GsonBuilder();
            gBuild.registerTypeAdapter(StoryPath.class, new StoryPathDeserializer());
            Gson gson = gBu'ild.create();

            String jsonFile = "foo";

            String json = JsonHelper.loadJSONFromPath(mStory.getStoryPathLibrary().buildPath(jsonFile));
            story = gson.fromJson(json, StoryPath.class);
            story.context = this;
            story.setCardReferences();
            story.setFileLocation(mStory.getCurrentStoryPath().buildPath(jsonFile));

            mStory.switchPaths(story);
            refreshCardView();
            mCardView.scrollToCard(0);

            return;
        }
        */

        if (mStoryPathLibrary.getCurrentStoryPath().getId().equals(pathParts[0])) {
            // reference targets this story path
            storyPath = mStoryPathLibrary.getCurrentStoryPath();
        } else {
            // reference targets a serialized story path
            for (Dependency dependency : mStoryPathLibrary.getCurrentStoryPath().getDependencies()) {
                if (dependency.getDependencyId().equals(pathParts[0])) {

                    // ASSUMES DEPENDENCIES ARE CORRECT RELATIVE TO PATH OF CURRENT LIBRARY
                    storyPath = JsonHelper.loadStoryPath(mStoryPathLibrary.getCurrentStoryPath().buildPath(dependency.getDependencyFile()), mStoryPathLibrary, this);
                    newStoryPath = true;

                    storyPathLibrary = JsonHelper.loadStoryPathLibrary(storyPath.buildPath(storyPath.getStoryPathLibraryFile()), this);

                    // loaded in reverse order, so need to set these references
                    storyPath.setStoryPathLibraryReference(storyPathLibrary);
                    storyPathLibrary.setCurrentStoryPath(storyPath);
                    storyPathLibrary.setCurrentStoryPathFile(mStoryPathLibrary.getCurrentStoryPath().buildPath(dependency.getDependencyFile()));

                    /*
                    GsonBuilder gBuild = new GsonBuilder();
                    gBuild.registerTypeAdapter(StoryPath.class, new StoryPathDeserializer());
                    Gson gson = gBuild.create();

                    String jsonFile = dependency.getDependencyFile();
                    String json = JsonHelper.loadJSONFromPath(mStoryPathLibrary.getCurrentStoryPath().buildPath(jsonFile));
                    story = gson.fromJson(json, StoryPath.class);

                    story.setContext(this);
                    story.setCardReferences();
                    story.setFileLocation(mStoryPathLibrary.getCurrentStoryPath().buildPath(jsonFile));
                    */


                }
            }
        }

        if (storyPath == null) {
            System.err.println("STORY PATH ID " + pathParts[0] + " WAS NOT FOUND");
            return;
        }

        Card card = storyPath.getCardById(cardPath);

        if (card == null) {
            System.err.println("CARD ID " + pathParts[1] + " WAS NOT FOUND");
            return;
        }

        if (newStoryPath) {

            // TODO: need additional code to save current story path

            // serialize current story path
            // add to story path files

            //mStoryPathLibrary.setCurrentStoryPath(storyPath);
            mStoryPathLibrary = storyPathLibrary;
            refreshCardViewXXX();
        }

        int cardIndex = mCardAdapter.mDataset.indexOf(card);

        if (cardIndex < 0) {
            System.err.println("CARD ID " + pathParts[1] + " IS NOT VISIBLE");
            return;
        }

        mRecyclerView.scrollToPosition(cardIndex);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d(TAG, "onActivityResult, requestCode:" + requestCode + ", resultCode: " + resultCode);
        if (resultCode == RESULT_OK) {
            // TODO : Remove this and allow Card View Controllers to be notified of data changes
//            setupCardView();

            if(requestCode == Constants.REQUEST_VIDEO_CAPTURE) {

                Uri uri = intent.getData();
                String path = getRealPathFromURI(getApplicationContext(), uri);
                Log.d(TAG, "onActivityResult, video path:" + path);
                String pathId = this.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString(Constants.PREFS_CALLING_CARD_ID, null); // FIXME should be done off the ui thread

                if (null == pathId || null == uri) {
                    return;
                }

                Card c = mStoryPathLibrary.getCurrentStoryPath().getCardById(pathId);
//                Card c = mStoryPathLibrary.getCardById(pathId); // FIXME temporarily routing around this to test clipcard

                if (c instanceof ClipCard) {
                    ClipCard cc = (ClipCard)c;
                    MediaFile mf = new MediaFile(path, Constants.VIDEO);
                    cc.saveMediaFile(mf);
                    mCardAdapter.changeCard(cc);
                } else {
                    Log.e(TAG, "card type " + c.getClass().getName() + " has no method to save " + Constants.VIDEO + " files");
                }

            } else if(requestCode == Constants.REQUEST_IMAGE_CAPTURE) {

                String path = getLastImagePath();
                Log.d(TAG, "onActivityResult, path:" + path);
                String pathId = this.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString(Constants.PREFS_CALLING_CARD_ID, null); // FIXME should be done off the ui thread

                if (null == pathId || null == path) {
                    return;
                }

                Card c = mStoryPathLibrary.getCurrentStoryPath().getCardById(pathId);

                if (c instanceof ClipCard) {
                    ClipCard cc = (ClipCard)c;
                    MediaFile mf = new MediaFile(path, Constants.PHOTO);
                    cc.saveMediaFile(mf);
                    mCardAdapter.changeCard(cc);
                } else {
                    Log.e(TAG, "card type " + c.getClass().getName() + " has no method to save " + Constants.PHOTO + " files");
                }

            } else if(requestCode == Constants.REQUEST_AUDIO_CAPTURE) {

                Uri uri = intent.getData();
                String path = getRealPathFromURI(getApplicationContext(), uri);
                Log.d(TAG, "onActivityResult, audio path:" + path);
                String pathId = this.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString(Constants.PREFS_CALLING_CARD_ID, null); // FIXME should be done off the ui thread

                if (null == pathId || null == uri) {
                    return;
                }

                Card c = mStoryPathLibrary.getCurrentStoryPath().getCardById(pathId);

                if (c instanceof ClipCard) {
                    ClipCard cc = (ClipCard)c;
                    MediaFile mf = new MediaFile(path, Constants.AUDIO);
                    cc.saveMediaFile(mf);
                    mCardAdapter.changeCard(cc);
                } else {
                    Log.e(TAG, "card class " + c.getClass().getName() + " has no method to save " + Constants.AUDIO + " files");
                }

            }
        }
    }

    public void saveStoryFile() {
        //Gson gson = new Gson();

        String savedFilePath = JsonHelper.saveStoryPath(mStoryPathLibrary.getCurrentStoryPath());
        mStoryPathLibrary.setCurrentStoryPathFile(savedFilePath);
        JsonHelper.saveStoryPathLibrary(mStoryPathLibrary);

        /*
        // prep and serialize story path library
        String json3 = gson.toJson(mStoryPathLibrary);

        // write to file, store path
        try {
            File storyPathLibraryFile = new File("/storage/emulated/0/Liger/default/TEST_LIB.json"); // need file naming plan
            FileOutputStream fos = new FileOutputStream(storyPathLibraryFile);
            if (!storyPathLibraryFile.exists()) {
                storyPathLibraryFile.createNewFile();
            }
            byte data[] = json3.getBytes();
            fos.write(data);
            fos.flush();
            fos.close();
            mStory.setStoryPathLibrary(null);
            mStory.setStoryPathLibraryFile(storyPathLibraryFile.getPath());
        } catch (IOException ioe) {
            Log.e(TAG, ioe.getMessage());
        }
        */

        /*
        // prep and serialize current story path
        mStoryPathLibrary.getCurrentStoryPath().setStoryPathLibraryReference(null);
        mStoryPathLibrary.getCurrentStoryPath().clearObservers();
        mStoryPathLibrary.getCurrentStoryPath().clearCardReferences(); // FIXME move this stuff into the model itself so we dont have to worry about it
        mStoryPathLibrary.getCurrentStoryPath().setContext(null);
        String json1 = gson.toJson(mStoryPathLibrary.getCurrentStoryPath());

        StoryPath sp = mStoryPathLibrary.getCurrentStoryPath();

        // write to file, store path
        try {
            File currentStoryPathFile = new File("/storage/emulated/0/Liger/default/TEST_PATH.json"); // need file naming plan
            FileOutputStream fos = new FileOutputStream(currentStoryPathFile);
            if (!currentStoryPathFile.exists()) {
                currentStoryPathFile.createNewFile();
            }
            byte data[] = json1.getBytes();
            fos.write(data);
            fos.flush();
            fos.close();
            mStoryPathLibrary.setCurrentStoryPath(null);
            mStoryPathLibrary.setCurrentStoryPathFile(currentStoryPathFile.getPath());
        } catch (IOException ioe) {
            Log.e(TAG, ioe.getMessage());
        }

        // prep and serialize top level story
        String json2 = gson.toJson(mStoryPathLibrary);

        // write to file
        try {
            File storyFile = new File("/storage/emulated/0/Liger/default/TEST_STORY.json");  // need file naming plan
            FileOutputStream fos = new FileOutputStream(storyFile);
            if (!storyFile.exists()) {
                storyFile.createNewFile();
            }
            byte data[] = json2.getBytes();
            fos.write(data);
            fos.flush();
            fos.close();
        } catch (IOException ioe) {
            Log.e(TAG, ioe.getMessage());
        }

        // restore links and continue
        // mStory.setStoryPathLibrary(mStoryPathLibrary);
        mStoryPathLibrary.setCurrentStoryPath(sp);

        mStoryPathLibrary.getCurrentStoryPath().setContext(this);
        mStoryPathLibrary.getCurrentStoryPath().setCardReferences();
        mStoryPathLibrary.getCurrentStoryPath().initializeObservers();
        mStoryPathLibrary.getCurrentStoryPath().setStoryPathLibraryReference(mStoryPathLibrary);
        */
    }

    /*
    public void loadStoryFile(File jsonFile) {
        GsonBuilder gBuild = new GsonBuilder();
        Gson gson = gBuild.create();

        // String storyJson = JsonHelper.loadJSONFromPath(jsonFile.getPath());
        // mStory = gson.fromJson(storyJson, Story.class);

        String libraryJson = JsonHelper.loadJSONFromPath(jsonFile.getPath());
        mStoryPathLibrary = gson.fromJson(libraryJson, StoryPathLibrary.class);

        // mStory.setStoryPathLibrary(mStoryPathLibrary);

        String pathJson = JsonHelper.loadJSONFromPath(mStoryPathLibrary.getCurrentStoryPathFile());
        initCardList(pathJson);
    }
    */

    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getLastImagePath() {
        final String[] imageColumns = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA };
        final String imageOrderBy = MediaStore.Images.Media._ID + " DESC";
        Cursor imageCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageColumns, null, null, imageOrderBy);
        String imagePath = null;

        if(imageCursor.moveToFirst()){
            int id = imageCursor.getInt(imageCursor.getColumnIndex(MediaStore.Images.Media._ID));
            imagePath = imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA));
            imageCursor.close();
            imageCursor = null;
        }

        return imagePath;
    }

    public int findSpot(Card card) {
        int newIndex = 0;

        if (mStoryPathLibrary.getCards().contains(card)) {
            int baseIndex = mStoryPathLibrary.getCards().indexOf(card);
            for (int i = (baseIndex - 1); i >= 0; i--) {
                Card previousCard = mStoryPathLibrary.getCards().get(i);
                if (mCardAdapter.mDataset.contains(previousCard)) {
                    newIndex = mCardAdapter.mDataset.indexOf(previousCard) + 1;

                    break;
                }
            }
        }

        if ((mStoryPathLibrary.getCurrentStoryPath() != null) && (mStoryPathLibrary.getCurrentStoryPath().getCards().contains(card))) {
            int baseIndex = mStoryPathLibrary.getCurrentStoryPath().getCards().indexOf(card);
            for (int i = (baseIndex - 1); i >= 0; i--) {
                Card previousCard = mStoryPathLibrary.getCurrentStoryPath().getCards().get(i);
                if (mCardAdapter.mDataset.contains(previousCard)) {
                    newIndex = mCardAdapter.mDataset.indexOf(previousCard) + 1;

                    break;
                }
            }
        }

        return newIndex;
    }

    public String checkCard(Card updatedCard) {

        if (updatedCard.getStateVisiblity()) {
            // new or updated

            if (mCardAdapter.mDataset.contains(updatedCard)) {
                return "UPDATE";
            } else {
                return "ADD";
            }
        } else {
            // deleted

            if (mCardAdapter.mDataset.contains(updatedCard)) {
                return "DELETE";
            }
        }

        return "ERROR";
    }
}
