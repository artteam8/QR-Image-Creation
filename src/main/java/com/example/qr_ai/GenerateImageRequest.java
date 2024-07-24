package com.example.qr_ai;

public class GenerateImageRequest {
    private final String model = "GigaChat";
    private final Message[] messages;
    private final String function_call = "auto";

    public GenerateImageRequest(String query) {
        this.messages = new Message[]{
                new Message("system", "Сгенерируй изображение по описанию"),
                new Message("user", (query+"гиперреализм, фотореалистичность"))
        };
    }

    private static class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
