package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
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

public class ChatFragment extends Fragment {

    private RecyclerView rvChatMessages;
    private LinearLayout layoutChatEmpty;
    private View layoutTyping;
    private TextInputEditText etChatInput;
    private MaterialButton btnSendMessage;
    private ChatMessageAdapter adapter;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));

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
        rvChatMessages.postDelayed(() -> {
            if (!isAdded()) return;
            layoutTyping.setVisibility(View.GONE);
            btnSendMessage.setEnabled(true);
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    false,
                    "I received your question. Once the AI backend is connected, this bubble will return advice plus tappable product recommendations.",
                    timeFormat.format(new Date()),
                    new ArrayList<>()
            ));
            rvChatMessages.scrollToPosition(adapter.getItemCount() - 1);
        }, 900);
    }
}
