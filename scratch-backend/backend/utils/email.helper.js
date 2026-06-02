const EmailSafetyHelpers = {
  escapeHtml(str) {
    if (!str) return '';
    const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(str).replace(/[&<>"']/g, function(m) { return map[m]; });
  },

  formatVND(amount) {
    return new Intl.NumberFormat('vi-VN', { 
      style: 'currency', currency: 'VND' 
    }).format(amount).replace('₫', 'đ'); 
  },

  truncateProductName(name, maxChars = 55) {
    const safeStr = this.escapeHtml(name);
    return safeStr.length > maxChars ? `${safeStr.substring(0, maxChars)}...` : safeStr;
  },

  getImageWithFallback(imageUrl, width = 80) {
    const src = imageUrl ? imageUrl : 'https://cdn.domain.com/placeholder.jpg';
    return `<img src="${src}" alt="Ảnh sản phẩm" width="${width}" height="auto" style="display:block; border:0; background-color:#f5f5f5;" />`;
  }
};

module.exports = EmailSafetyHelpers;
