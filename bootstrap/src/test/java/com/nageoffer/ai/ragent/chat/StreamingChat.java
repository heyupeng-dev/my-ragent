package com.nageoffer.ai.ragent.chat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class StreamingChat {

    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    // 替换成你自己的 API Key
    // private static final String API_KEY = "YOUR_API_KEY";
    private static final String API_KEY = "sk-kbwbxuihzygdpoyqozfatvouorzpnutgrzaooabuffrpbbwx";

    public static void main(String[] args) throws IOException {
        // 1. 构建请求体（注意 stream 设为 true）
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "Qwen/Qwen3-32B");
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", 1024);
        requestBody.addProperty("stream", true);

        // 构建 messages 数组
        JsonArray messages = new JsonArray();

        // system 消息：定义模型的行为规则
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "你是一个海盗船长，所有回答都要用海盗的语气，要加上'呀嗬'之类的口头禅。");
        messages.add(systemMsg);

        // user 消息：用户的问题
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Java 和 Python 哪个好？");
        messages.add(userMsg);

        // 将 messages 添加到 requestBody 中
        requestBody.add("messages", messages);

        // 2. 创建 OkHttp 客户端
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // 流式调用需要更长的读取超时
                .build();

        // 3. 构建请求
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        requestBody.toString(),
                        MediaType.parse("application/json")
                ))
                .build();

        // 4. 发送请求并逐行读取 SSE 响应
        Gson gson = new Gson();
        StringBuilder fullContent = new StringBuilder();

        System.out.println("=== 模型回答（流式输出）===");

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("请求失败，状态码：" + response.code());
                System.out.println("错误信息：" + response.body().string());
                return;
            }

            // 逐行读取响应体
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过空行
                if (line.isEmpty()) {
                    continue;
                }

                // 每行以 "data: " 开头，去掉前缀（6个字符）
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6);

                // 检查是否是结束标记
                if ("[DONE]".equals(data)) {
                    break;
                }

                // 解析 JSON，提取增量内容
                JsonObject chunk = gson.fromJson(data, JsonObject.class);
                JsonArray choices = chunk.getAsJsonArray("choices");
                if (choices != null && choices.size() > 0) {
                    JsonObject delta = choices.get(0).getAsJsonObject()
                            .getAsJsonObject("delta");
                    if (delta != null && delta.has("content")) {
                        JsonElement contentElement = delta.get("content");
                        if (!contentElement.isJsonNull()) {
                            String content = contentElement.getAsString();
                            // 实时打印增量内容（不换行，模拟打字效果）
                            System.out.print(content);
                            fullContent.append(content);
                        }
                    }
                }
            }
        }

        // 输出完毕，换行
        System.out.println();
        System.out.println();
        System.out.println("=== 完整回答 ===");
        System.out.println(fullContent);
    }
}
