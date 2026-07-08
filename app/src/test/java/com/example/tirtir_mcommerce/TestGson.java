import com.google.gson.Gson;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class TestGson {
    public static void main(String[] args) {
        String json = "{\"success\":true,\"data\":{\"_id\":\"6a4d4b6c092d5e2a01a93068\",\"userId\":\"6982b531907ce740387af9ef\",\"status\":\"Pending\",\"totalPrice\":47.1,\"shippingFee\":0,\"paymentMethod\":\"CARD\",\"isPaid\":false,\"shippingAddress\":{\"fullName\":\"Người dùng TirTir\",\"phone\":\"0909976498\",\"address\":\"123 Đường Láng, Láng Thượng, Đống Đa\",\"city\":\"Hồ Chí Minh\"},\"items\":[{\"product\":\"PRD-SK-SOS-01\",\"name\":\"SOS Serum\",\"quantity\":1,\"price\":25,\"shade\":\"50ml\"},{\"product\":\"PRD-MK-FIXER\",\"name\":\"Mask Fit Make Up Fixer\",\"quantity":1,\"price\":16,\"shade\":\"80ml (Full)\"}],\"invoiceUrl\":\"http://10.0.2.2:5000/api/v1/orders/6a4d4b6c092d5e2a01a93068/invoice\",\"adminNotes\":\"\",\"cancellationReason\":\"\",\"shippingDetails\":{\"trackingNumber\":\"\",\"carrier\":\"\"},\"history\":[],\"createdAt\":\"2026-07-07T18:54:36.630Z\",\"updatedAt\":\"2026-07-07T18:54:36.699Z\",\"__v\":0}}";
        
        Gson gson = new Gson();
        Type type = new TypeToken<ApiResponse<OrderResponse>>(){}.getType();
        ApiResponse<OrderResponse> response = gson.fromJson(json, type);
        
        System.out.println("Success: " + response.isSuccess());
        if (response.getData() != null) {
            System.out.println("Order ID: " + response.getData().getId());
            System.out.println("Total Price: " + response.getData().getTotalPrice());
            System.out.println("Items: " + (response.getData().getItems() != null ? response.getData().getItems().size() : "null"));
        } else {
            System.out.println("Data is null");
        }
    }
}
