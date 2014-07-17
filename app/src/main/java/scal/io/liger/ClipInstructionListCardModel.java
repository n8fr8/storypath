package scal.io.liger;

import java.util.ArrayList;

/**
 * Created by mnbogner on 7/17/14.
 */
public class ClipInstructionListCardModel extends CardModel {
    public String media_path;
    public String header;
    public ArrayList<String> bullet_list;

    public ClipInstructionListCardModel() {
        this.type = this.getClass().getName();
    }

    public String getMedia_path() {
        return media_path;
    }

    public void setMedia_path(String media_path) {
        this.media_path = media_path;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public ArrayList<String> getBullet_list() {
        return bullet_list;
    }

    public void setBullet_list(ArrayList<String> bullet_list) {
        this.bullet_list = bullet_list;
    }

    public void addBulletListItem(String bullet_listItem) {
        if (this.bullet_list == null)
            this.bullet_list = new ArrayList<String>();

        this.bullet_list.add(bullet_listItem);
    }
}
