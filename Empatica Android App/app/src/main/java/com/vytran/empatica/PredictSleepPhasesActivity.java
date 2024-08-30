package com.vytran.empatica;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import android.content.res.AssetFileDescriptor;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

public class PredictSleepPhasesActivity extends AppCompatActivity {

    private Interpreter tflite;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict_sleep_phases);

        resultView = findViewById(R.id.result_text_view);
        TextView inputTextView = findViewById(R.id.input_text_view);

        try {
            tflite = new Interpreter(loadModelFile());
            Map<String, Integer> vocabulary = loadVocabulary(this);

            // Example text input for spam detection
//            String inputText = "Subject: Reminder: Important Information About Your Account\n" +
//                    "Dear Vy,\n" +
//                    "\n" +
//                    "This is a friendly reminder to update your account information with Apple.\n" +
//                    "\n" +
//                    "We recently made some improvements to our website and it's important that your account information is up-to-date to ensure you continue to have uninterrupted access to our services.\n" +
//                    "\n" +
//                    "Updating your information is quick and easy. Simply click the link below and log in to your account. Then, follow the instructions on the screen to update your information.\n" +
//                    "Image of computer screen with a login pageOpens in a new window\n" +
//                    "www.creativefabrica.com\n" +
//                    "computer screen with a login page\n" +
//                    "\n" +
//                    "We appreciate your understanding and cooperation.\n" +
//                    "\n" +
//                    "Sincerely,\n" +
//                    "\n" +
//                    "The Apple Team";

            String inputText = "\n" +
                    "You've Won! $10,000,000 Awaits!\n" +
                    "Congratulations!  You've been randomly selected to receive a $10,000,000 cash prize!\n" +
                    "\n" +
                    "Simply click the link below to verify your information and claim your prize!\n" +
                    "\n" +
                    "DON'T MISS OUT!  This is a limited-time offer! ⏳ Time is running out! ⏱️\n" +
                    "\n" +
                    "Click Here Now! ➡️ http://fakeemailspam-domain.com.hello.ru ⬅️\n" +
                    "\n" +
                    "P.S. Don't forget to share this amazing news with your friends and family!";
            inputTextView.setText(inputText);

            // Preprocess the input text to match the model's input size
            ByteBuffer inputBuffer = preprocessInputText(inputText, vocabulary);

            // Adjust the output size according to the model's output
            float[][] outputData = new float[1][2]; // The model outputs a 2x1 tensor

            // Run inference
            tflite.run(inputBuffer, outputData);

            Log.i(HomeActivity.TAG, "Output probabilities: [" +
                    outputData[0][0] + ", " + outputData[0][1] + "]");

            // Display the result
            String result = outputData[0][0] > outputData[0][1] ? "Not Spam" : "Spam";
            resultView.setText("Prediction Result: " + result);

        } catch (IOException e) {
            e.printStackTrace();
            // Handle exceptions
        }
    }

    private Map<String, Integer> loadVocabulary(Context context) throws IOException {
        Map<String, Integer> dictionary = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("vocab.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    dictionary.put(parts[0], Integer.parseInt(parts[1]));
                }
            }
        }
        return dictionary;
    }

    private ByteBuffer preprocessInputText(String text, Map<String, Integer> dictionary) {
        // Split the text into words
        String[] words = text.split(" ");

        // Create a buffer for a 20x1 tensor (assuming each int is 4 bytes)
        ByteBuffer buffer = ByteBuffer.allocateDirect(20 * 4).order(ByteOrder.nativeOrder());

        for (int i = 0; i < 20; i++) {
            int token = dictionary.getOrDefault("<PAD>", 0); // Default to <PAD>
            if (i < words.length) {
                String word = words[i].toLowerCase();
                token = dictionary.getOrDefault(word, dictionary.getOrDefault("<UNKNOWN>", 2));
            }
            buffer.putInt(token);
        }

        buffer.rewind();
        return buffer;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("spam.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
