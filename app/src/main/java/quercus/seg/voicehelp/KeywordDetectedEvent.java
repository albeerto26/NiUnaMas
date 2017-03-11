package quercus.seg.voicehelp;

/**
 * Created by Fernando Diaz on 13/02/17.
 */

public class KeywordDetectedEvent {

    private String keyword;

    public KeywordDetectedEvent(String keyword) {
        this.keyword = keyword;
    }

    public String getDetectedKeyword() {
        return this.keyword;
    }

}
