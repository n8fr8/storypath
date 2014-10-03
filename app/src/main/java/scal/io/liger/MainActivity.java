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

import com.fima.cardsui.views.CardUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.MalformedJsonException;

import java.io.File;

import scal.io.liger.model.Card;
import scal.io.liger.model.ClipCard;
import scal.io.liger.model.Dependency;
import scal.io.liger.model.MediaFile;
import scal.io.liger.model.Story;
import scal.io.liger.model.StoryPath;
import scal.io.liger.model.StoryPathLibrary;


public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    Context mContext = this;
    CardUI mCardView;
    StoryPathLibrary mStoryPathLibrary;
    Story mStory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("MainActivity", "onCreate");
//        initApp();
        if (savedInstanceState == null) {
            Log.d(TAG, "onSaveInstanceState called with savedInstanceState");
            initApp();
        } else {
            Log.d(TAG, "onSaveInstanceState called with no saved state");
            Log.d("MainActivity", "savedInstanceState not null, check for and load storypath json");
            if (savedInstanceState.containsKey("storyPathJson")) {

                String json1 = savedInstanceState.getString("storyJson");
                initStory(json1);

                String json2 = savedInstanceState.getString("storyPathLibraryJson");
                initHook(json2);
                // maybe just initStoryPathLibraryModel?

                String json3 = savedInstanceState.getString("storyPathJson");
                initCardList(json3);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState called");

        if (mStory == null) {
            Log.d(TAG, "data not yet loaded, no state to save");
        } else {
            Gson gson = new Gson();
            mStory.getCurrentStoryPath().clearCardReferences(); // FIXME move this stuff into the model itself so we dont have to worry about it
            mStory.getCurrentStoryPath().context = null;
            mStory.getCurrentStoryPath().storyReference = null;

            // need to serialize Story as well?

            String json = gson.toJson(mStory.getCurrentStoryPath());
            outState.putString("storyPathJson", json);

            StoryPath sp = mStory.getCurrentStoryPath();
            mStory.setCurrentStoryPath(null);
            mStory.setStoryPathLibrary(null);


            String json2 = gson.toJson(mStory);
            outState.putString("storyJson", json2);


            String json3 = gson.toJson(mStoryPathLibrary);
            outState.putString("storyPathLibraryJson", json3);

            mStory.setStoryPathLibrary(mStoryPathLibrary);
            mStory.setCurrentStoryPath(sp);

            mStory.getCurrentStoryPath().context = this;
            mStory.getCurrentStoryPath().setCardReferences();
            mStory.getCurrentStoryPath().storyReference = mStory;

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
            builder.setTitle("Choose Story File(SdCard/Liger/)")
                .setItems(jsonFiles, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int index) {
                        File jsonFile = JsonHelper.setSelectedJSONFile(index);
                        String json = JsonHelper.loadJSON();

                        initHook(json, jsonFile);

                        // need to implement selection of story path based on hook

                        jsonFile = new File(mStoryPathLibrary.buildPath(mStoryPathLibrary.getStory_path_template_files().get(0)));
                        json = JsonHelper.loadJSONFromPath(jsonFile.getPath());

                        initCardList(json, jsonFile);
                    }
                });
        }

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void initHook(String json) {
        initHook(json, null);
    }

    private void initHook(String json, File jsonFile) {
        Log.d(TAG, "initHook called");

        // unsure what needs to be set up for the hook interface

        try {
            initStoryPathLibraryModel(json, jsonFile);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "JSON parsing error: " + e.getMessage().substring(e.getMessage().indexOf(":") + 2), Toast.LENGTH_LONG).show();
        }
    }

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

    private void initStoryPathLibraryModel(String json, File jsonFile) throws MalformedJsonException {
        Log.d(TAG, "initStoryPathLibraryModel called");
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

        if (mStory == null) {
            mStory = new Story();
        }

        mStory.setStoryPathLibrary(mStoryPathLibrary);
    }

    private void initCardList(String json) {
        initCardList(json, null);
    }

    private void initCardList(String json, File jsonFile) {
        Log.d(TAG, "initCardList called");
        mCardView = (CardUI) findViewById(R.id.cardsview);
        if (mCardView == null)
            return;

        mCardView.setSwipeable(false);

        try {
            initStoryPathModel(json, jsonFile);
            refreshCardView();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "JSON parsing error: " + e.getMessage().substring(e.getMessage().indexOf(":") + 2), Toast.LENGTH_LONG).show();
        }
    }

    private void initStoryPathModel(String json, File jsonFile) throws MalformedJsonException {
        Log.d(TAG, "initStoryPathModel called");
        GsonBuilder gBuild = new GsonBuilder();
        gBuild.registerTypeAdapter(StoryPath.class, new StoryPathDeserializer());
        Gson gson = gBuild.create();

        StoryPath sp = gson.fromJson(json, StoryPath.class);
        sp.context = this.mContext;
        sp.setCardReferences();

        // a story path model must have a file location to manage relative paths
        // if it is loaded from a saved state, the location should already be set
        if ((jsonFile == null) || (jsonFile.length() == 0)) {
            if ((sp.getFileLocation() == null) || (sp.getFileLocation().length() == 0)) {
                Log.e(TAG, "file location for story path " + sp.getId() + " could not be determined");
            }
        } else {
            sp.setFileLocation(jsonFile.getPath());
        }

        sp.setStoryReference(mStory);
        mStory.setCurrentStoryPath(sp);
    }

    public void refreshCardView () {
        Log.d(TAG, "refreshCardview called");
        if (mCardView == null)
            return;

        mCardView.clearCards();

        //add cardlist to view
        for (Card model : mStory.getCurrentStoryPath().getValidCards()) {
            mCardView.addCard(model.getCardView(mContext));
        }

        mCardView.refresh();
    }

    public void goToCard(String cardPath) throws MalformedJsonException {
        Log.d(TAG, "goToCard: " + cardPath);
        // assumes the format story::card::field::value
        String[] pathParts = cardPath.split("::");

        StoryPath story = null;
        boolean newStory = false;
        if (mStory.getCurrentStoryPath().getId().equals(pathParts[0])) {
            // reference targets this story path
            story = mStory.getCurrentStoryPath();
        } else {
            // reference targets a serialized story path
            for (Dependency dependency : mStory.getCurrentStoryPath().getDependencies()) {
                if (dependency.getDependencyId().equals(pathParts[0])) {
                    GsonBuilder gBuild = new GsonBuilder();
                    gBuild.registerTypeAdapter(StoryPath.class, new StoryPathDeserializer());
                    Gson gson = gBuild.create();

                    String jsonFile = dependency.getDependencyFile();
                    String json = JsonHelper.loadJSONFromPath(mStory.getCurrentStoryPath().buildPath(jsonFile));
                    story = gson.fromJson(json, StoryPath.class);

                    story.context = this.mContext;
                    story.setCardReferences();
                    story.setFileLocation(mStory.getCurrentStoryPath().buildPath(jsonFile));

                    newStory = true;
                }
            }
        }

        if (story == null) {
            System.err.println("STORY PATH ID " + pathParts[0] + " WAS NOT FOUND");
            return;
        }

        Card card = story.getCardById(cardPath);

        if (card == null) {
            System.err.println("CARD ID " + pathParts[1] + " WAS NOT FOUND");
            return;
        }

        int cardIndex = story.getValidCardIndex(card);

        if (cardIndex < 0) {
            System.err.println("CARD ID " + pathParts[1] + " IS NOT VISIBLE");
            return;
        }

        if (newStory) {

            // TODO: need additional code to save current story path

            // serialize current story path
            // add to story path files

            mStory.setCurrentStoryPath(story);
            refreshCardView();
        }

        mCardView.scrollToCard(cardIndex);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d(TAG, "onActivityResult, requestCode:" + requestCode + ", resultCode: " + resultCode);
        if (resultCode == RESULT_OK) {

            if(requestCode == Constants.REQUEST_VIDEO_CAPTURE) {

                Uri uri = intent.getData();
                String path = getRealPathFromURI(getApplicationContext(), uri);
                Log.d(TAG, "onActivityResult, video path:" + path);
                String pathId = mContext.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString(Constants.PREFS_CALLING_CARD_ID, null); // FIXME should be done off the ui thread

                if (null == pathId || null == uri) {
                    return;
                }

                Card c = mStory.getCurrentStoryPath().getCardById(pathId);

                if (c instanceof ClipCard) {
                    ClipCard cc = (ClipCard)c;

                    MediaFile mf = new MediaFile();
                    mf.setMedium(Constants.VIDEO);
                    mf.setPath(path);

                    cc.saveMediaFile(mf);
                } else {
                    Log.e(TAG, "card type " + c.getClass().getName() + " has no method to save " + Constants.VIDEO + " files");
                }

            } else if(requestCode == Constants.REQUEST_IMAGE_CAPTURE) {

                String path = getLastImagePath();
                Log.d(TAG, "onActivityResult, path:" + path);
                String pathId = mContext.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString(Constants.PREFS_CALLING_CARD_ID, null); // FIXME should be done off the ui thread

                if (null == pathId || null == path) {
                    return;
                }

                Card c = mStory.getCurrentStoryPath().getCardById(pathId);

                if (c instanceof ClipCard) {
                    ClipCard cc = (ClipCard)c;

                    MediaFile mf = new MediaFile();
                    mf.setMedium(Constants.PHOTO);
                    mf.setPath(path);

                    cc.saveMediaFile(mf);
                } else {
                    Log.e(TAG, "card type " + c.getClass().getName() + " has no method to save " + Constants.PHOTO + " files");
                }

            } else if(requestCode == Constants.REQUEST_AUDIO_CAPTURE) {

                Uri uri = intent.getData();
                String path = getRealPathFromURI(getApplicationContext(), uri);
                Log.d(TAG, "onActivityResult, audio path:" + path);
                String pathId = mContext.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString(Constants.PREFS_CALLING_CARD_ID, null); // FIXME should be done off the ui thread

                if (null == pathId || null == uri) {
                    return;
                }

                Card c = mStory.getCurrentStoryPath().getCardById(pathId);

                if (c instanceof ClipCard) {
                    ClipCard cc = (ClipCard)c;

                    MediaFile mf = new MediaFile();
                    mf.setMedium(Constants.AUDIO);
                    mf.setPath(path);

                    cc.saveMediaFile(mf);
                } else {
                    Log.e(TAG, "card class " + c.getClass().getName() + " has no method to save " + Constants.AUDIO + " files");
                }

            }
        }
    }

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
}