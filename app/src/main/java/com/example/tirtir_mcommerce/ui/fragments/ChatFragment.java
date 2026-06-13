package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.repository.ChatRepository;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.activities.SkinAnalysisActivity;
import com.example.tirtir_mcommerce.ui.adapters.ChatMessageAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatFragment extends Fragment {

    private RecyclerView rvChatMessages;
    private LinearLayout layoutChatEmpty;
    private View layoutTyping;
    private TextInputEditText etChatInput;
    private MaterialButton btnSendMessage;
    private ChatMessageAdapter adapter;
    private ChatRepository chatRepository;
    private View offlineBanner;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvChatMessages = view.findViewById(R.id.rvChatMessages);
        layoutChatEmpty = view.findViewById(R.id.layoutChatEmpty);
        layoutTyping = view.findViewById(R.id.layoutTyping);
        etChatInput = view.findViewById(R.id.etChatInput);
        btnSendMessage = view.findViewById(R.id.btnSendMessage);
        offlineBanner = view.findViewById(R.id.tvChatOfflineBanner);
        chatRepository = new ChatRepository(requireContext());

        adapter = new ChatMessageAdapter(product -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra("PRODUCT_ID", product.productId);
            startActivity(intent);
        });
        rvChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChatMessages.setAdapter(adapter);

        btnSendMessage.setOnClickListener(v -> sendCurrentMessage());
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });

        view.findViewById(R.id.btnOpenSkinAnalysis).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SkinAnalysisActivity.class)));
        bindPrompt(view, R.id.chipPromptSensitive);
        bindPrompt(view, R.id.chipPromptRoutine);
        bindPrompt(view, R.id.chipPromptIngredient);
        bindProductContextIfAvailable();
        updateConnectivityState();
        loadHistory();
    }

    private void bindProductContextIfAvailable() {
        Bundle args = getArguments();
        if (args == null) return;
        String productName = args.getString("PRODUCT_NAME");
        String productId = args.getString("PRODUCT_ID");
        if ((productName == null || productName.isEmpty()) && (productId == null || productId.isEmpty())) {
            return;
        }

        layoutChatEmpty.setVisibility(View.GONE);
        String displayName = productName != null && !productName.isEmpty() ? productName : "this product";
        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                false,
                "I am looking at " + displayName + ". Ask me about ingredients, skin fit, routine order, or what to pair with it.",
                timeFormat.format(new Date()),
                new ArrayList<>()
        ));
    }

    private void bindPrompt(View root, int chipId) {
        Chip chip = root.findViewById(chipId);
        if (chip != null) {
            chip.setOnClickListener(v -> {
                etChatInput.setText(chip.getText());
                etChatInput.setSelection(etChatInput.length());
            });
        }
    }

    private void sendCurrentMessage() {
        String text = etChatInput.getText() == null ? "" : etChatInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        layoutChatEmpty.setVisibility(View.GONE);
        adapter.addMessage(new ChatMessageAdapter.ChatMessage(true, text, timeFormat.format(new Date()), new ArrayList<>()));
        rvChatMessages.scrollToPosition(adapter.getItemCount() - 1);
        etChatInput.setText("");

        layoutTyping.setVisibility(View.VISIBLE);
        btnSendMessage.setEnabled(false);
        StringBuilder streamed = new StringBuilder();
        int[] botPosition = {-1};
        chatRepository.sendMessage(text, new ChatRepository.StreamListener() {
            @Override
            public void onChunk(String chunk) {
                if (!isAdded() || chunk == null || chunk.isEmpty()) return;
                requireActivity().runOnUiThread(() -> {
                    streamed.append(chunk);
                    ChatMessageAdapter.ChatMessage message = new ChatMessageAdapter.ChatMessage(
                            false, streamed.toString(), timeFormat.format(new Date()), new ArrayList<>());
                    if (botPosition[0] < 0) {
                        layoutTyping.setVisibility(View.GONE);
                        botPosition[0] = adapter.addAndReturnPosition(message);
                    } else {
                        adapter.updateMessage(botPosition[0], message);
                    }
                    rvChatMessages.scrollToPosition(adapter.getItemCount() - 1);
                });
            }

            @Override
            public void onDone(ChatRepository.ChatResult result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String finalText = result.message == null || result.message.isEmpty()
                            ? streamed.toString() : result.message;
                    List<ChatMessageAdapter.RecommendedProduct> recommendations = new ArrayList<>();
                    for (ChatRepository.Suggestion suggestion : result.suggestions) {
                        recommendations.add(new ChatMessageAdapter.RecommendedProduct(
                                suggestion.productId, suggestion.name));
                    }
                    ChatMessageAdapter.ChatMessage message = new ChatMessageAdapter.ChatMessage(
                            false, finalText, timeFormat.format(new Date()), recommendations);
                    if (botPosition[0] < 0) {
                        botPosition[0] = adapter.addAndReturnPosition(message);
                    } else {
                        adapter.updateMessage(botPosition[0], message);
                    }
                    finishRequest();
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String display = message == null || message.isEmpty()
                            ? "The advisor is unavailable. Please try again." : message;
                    ChatMessageAdapter.ChatMessage error = new ChatMessageAdapter.ChatMessage(
                            false, display, timeFormat.format(new Date()), new ArrayList<>());
                    if (botPosition[0] < 0) adapter.addMessage(error);
                    else adapter.updateMessage(botPosition[0], error);
                    finishRequest();
                });
            }
        });
    }

    private void finishRequest() {
        layoutTyping.setVisibility(View.GONE);
        btnSendMessage.setEnabled(true);
        rvChatMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
    }

    private void loadHistory() {
        chatRepository.loadHistory(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null
                        || response.body().getData() == null || response.body().getData().isEmpty()) return;
                List<ChatMessageAdapter.ChatMessage> history = new ArrayList<>();
                for (Map<String, Object> item : response.body().getData()) {
                    String sender = value(item.get("sender"));
                    String text = value(item.get("text"));
                    if (text.isEmpty()) continue;
                    history.add(new ChatMessageAdapter.ChatMessage(
                            "user".equalsIgnoreCase(sender), text, "", extractRecommendations(item)));
                }
                requireActivity().runOnUiThread(() -> {
                    if (history.isEmpty()) return;
                    layoutChatEmpty.setVisibility(View.GONE);
                    adapter.submitMessages(history);
                    rvChatMessages.scrollToPosition(history.size() - 1);
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                // Local empty state remains useful while offline.
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessageAdapter.RecommendedProduct> extractRecommendations(Map<String, Object> message) {
        List<ChatMessageAdapter.RecommendedProduct> result = new ArrayList<>();
        Object dataObject = message.get("productData");
        if (!(dataObject instanceof Map)) return result;
        Map<String, Object> productData = (Map<String, Object>) dataObject;
        Object recommendations = productData.get("recommendations");
        if (recommendations instanceof List) {
            for (Object item : (List<?>) recommendations) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> product = (Map<String, Object>) item;
                String id = value(product.get("id"));
                String name = value(product.get("name"));
                if (!id.isEmpty() && !name.isEmpty()) {
                    result.add(new ChatMessageAdapter.RecommendedProduct(id, name));
                }
            }
        }
        return result;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void updateConnectivityState() {
        ConnectivityManager manager =
                (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager == null ? null : manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        boolean online = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE);
    }
}
