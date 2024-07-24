package com.example.qr_ai;

import com.google.gson.annotations.SerializedName;

public class GenerateImageResponse {
    @SerializedName("choices")
    private Choice[] choices;

    public Choice[] getChoices() {
        return choices;
    }

    public String getFileId() {
        if (choices != null && choices.length > 0) {
            String content = choices[0].getMessage().getContent();
            int startIndex = content.indexOf("src=\"") + 5;
            int endIndex = content.indexOf("\"", startIndex);
            if (startIndex != -1 && endIndex != -1) {
                return content.substring(startIndex, endIndex);
            }
        }
        return null;
    }

    class Choice {
        @SerializedName("message")
        private Message message;

        public Message getMessage() {
            return message;
        }
    }

    class Message {
        @SerializedName("content")
        private String content;

        public String getContent() {
            return content;
        }
    }
}
