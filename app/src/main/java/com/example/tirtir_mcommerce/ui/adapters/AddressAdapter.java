package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Address;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Adapter hiển thị danh sách địa chỉ giao hàng trong ProfileFragment.
 *
 * Tính năng (theo Syllabus):
 * - Popup Menu khi bấm nút "..." (Context Menu theo yêu cầu môn học)
 * - AlertDialog xác nhận khi xóa địa chỉ (Dialog theo yêu cầu môn học)
 * - Giao tiếp với Fragment thông qua interface (loose coupling)
 */
public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.AddressViewHolder> {

    private List<Address> addressList;
    private final AddressActionListener listener;

    public AddressAdapter(List<Address> addressList, AddressActionListener listener) {
        this.addressList = addressList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AddressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);
        return new AddressViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AddressViewHolder holder, int position) {
        Address address = addressList.get(position);
        holder.bind(address);
    }

    @Override
    public int getItemCount() {
        return addressList == null ? 0 : addressList.size();
    }

    public void updateData(List<Address> newList) {
        this.addressList = newList;
        notifyDataSetChanged();
    }

    // ===========================
    // VIEW HOLDER
    // ===========================

    class AddressViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName, tvPhone, tvAddress;
        private final Chip chipDefault;
        private final ImageButton btnMore;

        public AddressViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvReceiverName);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvAddress = itemView.findViewById(R.id.tvAddressLine);
            chipDefault = itemView.findViewById(R.id.tvAddressLabel);
            btnMore = itemView.findViewById(R.id.btnEditAddress);
        }

        public void bind(Address address) {
            tvName.setText(address.getFullName());
            tvPhone.setText(address.getPhone());
            tvAddress.setText(address.getFormattedAddress());

            // Hiện/ẩn chip "Mặc định"
            chipDefault.setVisibility(address.isDefault() ? View.VISIBLE : View.GONE);

            // ===== POPUP MENU (Yêu cầu môn học: Context Menu) =====
            btnMore.setOnClickListener(view -> {
                PopupMenu popup = new PopupMenu(view.getContext(), view);
                popup.getMenuInflater().inflate(R.menu.address_context_menu, popup.getMenu());

                // Ẩn "Đặt mặc định" nếu đây đã là địa chỉ mặc định
                popup.getMenu().findItem(R.id.menu_set_default)
                        .setVisible(!address.isDefault());

                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.menu_edit_address) {
                        listener.onEditAddress(address);
                        return true;
                    } else if (id == R.id.menu_set_default) {
                        listener.onSetDefault(address);
                        return true;
                    } else if (id == R.id.menu_delete_address) {
                        // ===== ALERT DIALOG (Yêu cầu môn học: Dialog) =====
                        new AlertDialog.Builder(view.getContext())
                                .setTitle(R.string.dialog_confirm_title)
                                .setMessage(R.string.dialog_delete_address)
                                .setPositiveButton(R.string.btn_delete, (dialog, which) ->
                                        listener.onDeleteAddress(address))
                                .setNegativeButton(R.string.btn_cancel, null)
                                .show();
                        return true;
                    }
                    return false;
                });

                popup.show();
            });
        }
    }

    // ===========================
    // INTERFACE (Callback to Fragment)
    // ===========================

    public interface AddressActionListener {
        void onEditAddress(Address address);
        void onDeleteAddress(Address address);
        void onSetDefault(Address address);
    }
}
