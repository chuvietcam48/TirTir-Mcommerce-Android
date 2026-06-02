import sys
import pandas as pd
import json
import os
from sklearn.feature_extraction.text import CountVectorizer
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import make_pipeline

# 1. SETUP & LOAD DATA
sys.stdout.reconfigure(encoding='utf-8')

current_dir = os.path.dirname(os.path.abspath(__file__))
csv_path = os.path.join(current_dir, 'products.csv')
        
try:
    df = pd.read_csv(csv_path)
    features = ['Skin_Type_Target', 'Main_Concern', 'Key_Ingredients', 'Description_Short', 'Category', 'Name']
    for feature in features:
        if feature in df.columns:
            df[feature] = df[feature].fillna('')
except Exception as e:
    print(json.dumps({"error": f"Lỗi load data: {str(e)}"}))
    sys.exit(1)

# 2. TRAINING DATA (MỞ RỘNG)
# Format: (Câu mẫu, Nhãn ý định)
train_data = [
    # --- TƯ VẤN SẢN PHẨM ---
    ("da dầu dùng gì", "consultation"), ("tư vấn giúp mình", "consultation"),
    ("loại nào tốt cho da khô", "consultation"), ("da mụn", "consultation"),
    ("tìm sản phẩm căng bóng", "consultation"), ("cushion nào che phủ tốt", "consultation"),
    ("da nhạy cảm dùng được không", "consultation"), ("da hỗn hợp", "consultation"),
    ("tư vấn màu", "consultation"), ("chọn tone", "consultation"),
    
    # --- HỎI GIÁ ---
    ("giá bao nhiêu", "price"), ("cái này bao tiền", "price"), 
    ("mắc không", "price"), ("báo giá", "price"), ("đang sale không", "price"),

    # --- CHÀO HỎI ---
    ("hi", "greeting"), ("hello", "greeting"), ("chào shop", "greeting"), 
    ("bạn ơi", "greeting"), ("có ai ở đó không", "greeting"),

    # --- THÔNG TIN / HDSD ---
    ("thành phần là gì", "info"), ("công dụng", "info"), 
    ("cách dùng", "info"), ("xài sao", "info"), ("hạn sử dụng", "info"),

    # --- GIAO HÀNG / SHIP ---
    ("ship bao lâu", "shipping"), ("có freeship không", "shipping"),
    ("phí ship thế nào", "shipping"), ("giao hàng nhanh không", "shipping"),

    # --- ĐỔI TRẢ ---
    ("đổi trả thế nào", "return"), ("bảo hành không", "return"),
    ("hàng lỗi thì sao", "return"), ("chính sách đổi trả", "return")
]

# Train Model
nlp_model = make_pipeline(CountVectorizer(ngram_range=(1, 2)), MultinomialNB())
nlp_model.fit([x[0] for x in train_data], [x[1] for x in train_data])

# 3. TỪ ĐIỂN MAPPING (RULE-BASED ENHANCEMENT)
keywords_map = {
    "dầu": {"tag": "oily", "score_field": "Skin_Type_Target"},
    "nhờn": {"tag": "oily", "score_field": "Skin_Type_Target"},
    "khô": {"tag": "dry", "score_field": "Skin_Type_Target"},
    "mụn": {"tag": "acne", "score_field": "Main_Concern"}, # Mở rộng logic
    "nhạy cảm": {"tag": "sensitive", "score_field": "Skin_Type_Target"},
    "che phủ": {"tag": "coverage", "score_field": "Main_Concern"},
    "căng bóng": {"tag": "glow", "score_field": "Main_Concern"},
    "lì": {"tag": "matte", "score_field": "Main_Concern"},
    "tự nhiên": {"tag": "natural", "score_field": "Main_Concern"},
    "nắng": {"tag": "sun", "score_field": "Category"},
    "trắng": {"tag": "brightening", "score_field": "Main_Concern"},
    "đỏ": {"tag": "red", "score_field": "Name"}, # Red Cushion
    "hồng": {"tag": "all-cover", "score_field": "Name"}, # Pink Cushion
    "đen": {"tag": "mask fit", "score_field": "Name"},
    "bạc": {"tag": "aura", "score_field": "Name"}, # Aura/Silver
}

# 4. HÀM GỢI Ý THÔNG MINH (SMART SCORING)
def smart_recommend(user_query):
    query_lower = user_query.lower()
    
    # Tạo cột điểm cho mỗi sản phẩm
    df['score'] = 0
    
    matched_tags = []
    
    # Quét từ khóa và cộng điểm
    for word, logic in keywords_map.items():
        if word in query_lower:
            target_val = logic["tag"]
            field = logic["score_field"]
            
            matched_tags.append(word)
            
            # Cộng 10 điểm nếu khớp đúng cột mục tiêu
            # (Ví dụ: từ khóa "dầu" khớp với sản phẩm có Skin_Type_Target chứa "Oily")
            mask = df[field].astype(str).str.contains(target_val, case=False, na=False)
            df.loc[mask, 'score'] += 10
            
            # Cộng 2 điểm nếu khớp ở mô tả (Description) - tìm vớt
            mask_desc = df['Description_Short'].astype(str).str.contains(target_val, case=False, na=False)
            df.loc[mask_desc, 'score'] += 2

    # Nếu không bắt được keyword nào, trả về None để xử lý fallback
    if df['score'].max() == 0:
        return None, []

    # Lấy sản phẩm điểm cao nhất
    best_match = df.sort_values(by='score', ascending=False).iloc[0]
    return best_match, matched_tags

# 5. MAIN PROCESS
def process_message(msg):
    try:
        intent = nlp_model.predict([msg])[0]
        result = {
            "intent": intent, 
            "message": "", 
            "data": None,
            "type": "text" # text | product | list
        }

        if intent == "greeting":
            result["message"] = "👋 Chào bạn! Mình là Tirtir AI Assistant.\nMình có thể giúp bạn tìm cushion phù hợp với loại da, báo giá, hoặc hướng dẫn sử dụng."
        
        elif intent == "shipping":
            result["message"] = "📦 Bên mình freeship cho đơn từ 500k. Thời gian giao hàng nội thành là 1-2 ngày, ngoại thành 3-4 ngày ạ."
            
        elif intent == "return":
            result["message"] = "Sản phẩm được đổi trả trong 7 ngày nếu có lỗi sản xuất hoặc kích ứng (cần video mở hộp). Bạn yên tâm nhé!"

        elif intent == "info":
             result["message"] = "Các sản phẩm Tirtir nổi tiếng với chiết xuất thiên nhiên, đặc biệt là dòng Mask Fit Cushion bền màu suốt 72h."

        elif intent == "consultation" or intent == "price":
            # Chạy logic tìm kiếm sản phẩm
            product, tags = smart_recommend(msg)
            
            if product is not None:
                # Format phản hồi xịn hơn
                result["type"] = "product"
                result["message"] = f"Dựa trên từ khóa '{', '.join(tags)}', mình đề xuất:"
                result["data"] = {
                    "id": str(product["Product_ID"]),
                    "name": product["Name"],
                    "price": float(product["Price"]),
                    "image": product["Thumbnail_Images"],
                    "desc": product["Description_Short"],
                    "slug": product["Product_Slug"] if "Product_Slug" in product else ""
                }
                
                # Nếu hỏi giá, nói thêm về giá
                if intent == "price":
                    result["message"] = f"Sản phẩm {product['Name']} hiện có giá ${product['Price']}."
            else:
                result["message"] = "🤔 Bạn có thể nói rõ hơn về loại da của mình không? (Ví dụ: da dầu, da khô, da mụn...)"

        else:
            result["message"] = "Mình chưa hiểu rõ lắm. Bạn thử hỏi: 'Da dầu dùng cushion nào?' xem sao!"

        print(json.dumps(result, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"error": str(e), "message": "Lỗi hệ thống AI"}))

if __name__ == "__main__":
    if len(sys.argv) > 1:
        process_message(sys.argv[1])
    else:
        print(json.dumps({"message": "No input provided"}))