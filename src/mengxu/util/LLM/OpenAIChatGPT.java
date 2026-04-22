package mengxu.util.LLM;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class OpenAIChatGPT {
//    private static final String API_KEY = "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";
    private static final String API_KEY = "sk-proj-dFgrEqc0uSc39RYU3nF3T3BlbkFJuvroDF5Z7lBoNzEeDuej"; // the CFAR one can not be used for now! Only test once.
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    public static void main(String[] args) throws IOException {
        // Create the HTTP client
        OkHttpClient client = new OkHttpClient();

        // Create the JSON request body
        JSONObject json = new JSONObject();
        json.put("model", "gpt-3.5-turbo"); // Use "gpt-3.5-turbo" if GPT-4 is not available
        json.put("messages", new JSONArray()
                .put(new JSONObject()
                        .put("role", "system")
                        .put("content", "You are a helpful assistant."))
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", "Please analyze the file located at /Users/mengxu/IdeaProjects/GPJSS-master/src/mengxu/algorithm/LLM/WarmStart/population_file_new.txt and derive meaningful insights into why and how these individuals deliver good scheduling performance. In this context, Tree 0 represents the sequencing rule, and Tree 1 represents the routing rule for dynamic flexible job shop scheduling problems. Using the gathered insights, create a new file named population_file_new_gene.txt in the same directory. This new file should feature well-crafted individuals expected to yield better scheduling performance. The structure of population_file_new_gene.txt should remain consistent with the original file, with only the tree structures being newly generated and unique.\n" +
                                "\n" +
                                "The steps to achieve this are as follows:\n" +
                                "\n" +
                                "Read the contents of the original file /Users/mengxu/IdeaProjects/GPJSS-master/src/mengxu/algorithm/LLM/WarmStart/population_file_new.txt.\n" +
                                "Analyze the individuals in the file to identify the factors contributing to their good scheduling performance.\n" +
                                "Design new tree structures aimed at enhancing scheduling performance.\n" +
                                "Save the newly designed individuals with updated tree structures to population_file_new_gene.txt in the same directory.")));

//        String mockResponse = "{ 'choices': [{'text': 'Mocked response'}] }";
//        System.out.println("Response: " + mockResponse);

        // Create the HTTP request
        // Create the HTTP request body
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
        );
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        // Send the request
        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            System.out.println("Response: " + response.body().string());
        } else {
            System.err.println("Error: " + response.body().string());
        }
    }
}
