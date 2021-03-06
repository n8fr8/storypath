package scal.io.liger.model;

import timber.log.Timber;

import android.util.Log;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.Observable;

/**
 * @author Matt Bogner
 * @author Josh Steiner
 */
public class TipCollectionHeadlessCard extends HeadlessCard {

    public final String TAG = this.getClass().getSimpleName();

    @Expose private ArrayList<Tip> tips; // NOT SURE THIS WILL WORK WITH INNER CLASS...

    public TipCollectionHeadlessCard(ArrayList<Tip> tips) {
        super();
        this.tips = tips;
    }

    public ArrayList<Tip> getTips() {
        return tips;
    }

    // FIXME opt: this can probably be more efficient
    public ArrayList<Tip> getTipsByTags(ArrayList<String> tags) {
        ArrayList<Tip> matchingTips = new ArrayList<Tip>();
        for (Tip tip: tips) {
            boolean match = false;
            for (String tipTag: tip.tags) {
                for (String tag: tags) {
                    if (tag.toLowerCase().equals(tipTag.toLowerCase())) {
                        matchingTips.add(tip);
                        match = true;
                        break;
                    }
                }
                if (match) break;
            }
        }
        return matchingTips;
    }

    public ArrayList<String> getTipsTextByTags(ArrayList<String> tags) {
        ArrayList<Tip> _tips = getTipsByTags(tags);
        ArrayList<String> texts = null;
        if (_tips.size() > 0) {
            texts = new ArrayList<String>();
            for (Tip tip : _tips) {
                texts.add(tip.text);
            }
        }
        return texts;
    }

    public TipCollectionHeadlessCard() {
        super();
    }

    @Override
    public void update(Observable observable, Object o) {
        if (!(observable instanceof Card)) {
            Timber.e("update notification received from non-card observable");
            return;
        }
        if (storyPath == null) {
            Timber.e("STORY PATH REFERENCE NOT FOUND, CANNOT SEND NOTIFICATION");
            return;
        }

//        Card card = (Card)observable;
//
//        // recycling stateVisibility to ensure this only triggers once
//        if (!stateVisiblity) {
//            if (checkReferencedValues()) {
//                stateVisiblity = true;
//
//                if (action.equals("LOAD")) {
//                    Timber.d("LOADING FILE: " + target);
//
//                    loadStoryPath(target);
//                } else {
//                    Timber.e("UNSUPPORTED ACTION: " + action);
//                }
//            }
//        }
    }

    @Override
    public void copyText(Card card) {
        if (!(card instanceof TipCollectionHeadlessCard)) {
            Timber.e("CARD " + card.getId() + " IS NOT AN INSTANCE OF TipCollectionHeadlessCard");
        }
        if (!(this.getId().equals(card.getId()))) {
            Timber.e("CAN'T COPY STRINGS FROM " + card.getId() + " TO " + this.getId() + " (CARD ID'S MUST MATCH)");
            return;
        }

        TipCollectionHeadlessCard castCard = (TipCollectionHeadlessCard)card;

        this.title = castCard.getTitle();
        this.tips = castCard.getTips();
    }
}
