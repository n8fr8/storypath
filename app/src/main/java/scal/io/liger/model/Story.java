package scal.io.liger.model;

import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by mnbogner on 9/29/14.
 */
public class Story {

    private StoryPathLibrary storyPathLibrary; // not serialized
    private StoryPath currentStoryPath; // not serialized
    private ArrayList<String> story_path_instance_files;
    private HashMap<String, MediaFile> media_files;

    public Story() {
        // required for JSON/GSON
    }

    public Story(StoryPathLibrary storyPathLibrary) {
        this.storyPathLibrary = storyPathLibrary;
    }

    public StoryPathLibrary getStoryPathLibrary() {
        return storyPathLibrary;
    }

    public void setStoryPathLibrary(StoryPathLibrary storyPathLibrary) {
        this.storyPathLibrary = storyPathLibrary;
    }

    public StoryPath getCurrentStoryPath() {
        return currentStoryPath;
    }

    public void setCurrentStoryPath(StoryPath currentStoryPath) {
        this.currentStoryPath = currentStoryPath;
    }

    public ArrayList<String> getStory_path_instance_files() {
        return story_path_instance_files;
    }

    public void setStory_path_instance_files(ArrayList<String> story_path_instance_files) {
        this.story_path_instance_files = story_path_instance_files;
    }

    public void addStoryPathInstanceFile(String file) {
        if (this.story_path_instance_files == null) {
            this.story_path_instance_files = new ArrayList<String>();
        }

        this.story_path_instance_files.add(file);
    }

    public HashMap<String, MediaFile> getMedia_files() {
        return media_files;
    }

    public void setMedia_files(HashMap<String, MediaFile> media_files) {
        this.media_files = media_files;
    }

    public void saveMediaFile(String uuid, MediaFile file) {

        if (this.media_files == null) {
            this.media_files = new HashMap<String, MediaFile>();
        }
        this.media_files.put(uuid, file);
    }

    public MediaFile loadMediaFile(String uuid) {
        return media_files.get(uuid);
    }

    // need to determine whether users are allowed to delete files that are referenced by cards
    // need to determine whether to automatically delete files when they are no longer referenced
    public void deleteMediaFile(String uuid) {
        if ((media_files == null) || (!media_files.keySet().contains(uuid))) {
            Log.e(this.getClass().getName(), "key was not found, cannot delete file");
            return;
        }

        media_files.remove(uuid);

        // NEED TO DELETE ACTUAL FILE...
    }

    // NOT YET SURE WHERE TO PRESENT PATH OPTIONS AND DESERIALIZE NEW PATH
    public void switchPaths(StoryPath newPath) {
        // export clip metadata
        // also may need to export stored values
        StoryPath oldPath = this.getCurrentStoryPath();
        ArrayList<ClipMetadata> metadata = oldPath.exportMetadata();

        // serialize current story path
        Gson gson = new Gson();
        oldPath.clearCardReferences(); // FIXME move this stuff into the model itself so we dont have to worry about it
        oldPath.setContext(null);
        oldPath.setStoryReference(null);
        String json = gson.toJson(oldPath);

        try {
            File oldPathFile = new File(oldPath.buildPath(oldPath.getId() + ".path"));
            PrintStream ps = new PrintStream(new FileOutputStream(oldPathFile.getPath()));
            ps.print(json);
            // store file path
            // NOT YET SURE HOW TO HANDLE VERSIONS OR DUPLICATES
            this.addStoryPathInstanceFile(oldPathFile.getPath());
        } catch (FileNotFoundException fnfe) {
            Log.e(this.getClass().getName(), "could not file file: " + fnfe.getMessage());
        } catch (Exception e) {
            Log.e(this.getClass().getName(), "other exception: " + e.getMessage());
        }

        // import clip metadata
        newPath.importMetadata(metadata);

        // should this be done externally?
        newPath.setStoryReference(this);

        // update current story path
        this.setCurrentStoryPath(newPath);

        // NOTIFY/REFRESH HERE OR LET THAT BE HANDLED BY WHATEVER CALLS THIS?
    }
}