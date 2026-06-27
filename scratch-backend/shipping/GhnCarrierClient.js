class GhnCarrierClient {
    constructor() {
        this.client = {
            get: async (url, config) => {
                if (url.includes('province')) {
                    return { data: { code: 200, data: [
                        { ProvinceID: 1, ProvinceName: "Hà Nội" },
                        { ProvinceID: 2, ProvinceName: "Hồ Chí Minh" },
                        { ProvinceID: 3, ProvinceName: "Đà Nẵng" }
                    ]}};
                }
                if (url.includes('district')) {
                    return { data: { code: 200, data: [
                        { DistrictID: 101, DistrictName: "Quận 1" },
                        { DistrictID: 102, DistrictName: "Quận 2" },
                        { DistrictID: 103, DistrictName: "Quận 3" }
                    ]}};
                }
                if (url.includes('ward')) {
                    return { data: { code: 200, data: [
                        { WardCode: "1001", WardName: "Phường 1" },
                        { WardCode: "1002", WardName: "Phường 2" },
                        { WardCode: "1003", WardName: "Phường 3" }
                    ]}};
                }
                return { data: { code: 404 } };
            }
        };
    }

    async getAvailableServices(fromDistrict, toDistrict) {
        return [
            { service_id: 1, short_name: "Giao hàng tiêu chuẩn" },
            { service_id: 2, short_name: "Giao hàng hỏa tốc" }
        ];
    }

    async calculateFee(serviceId, fromDistrict, toDistrict, toWardCode, weight, length, width, height, orderValue) {
        if (serviceId === 1) return { total: 30000 };
        return { total: 50000 };
    }

    async getLeadTime(fromDistrict, fromWardCode, toDistrict, toWardCode, serviceId) {
        const leadtime = Math.floor(Date.now() / 1000) + (serviceId === 1 ? 3 * 86400 : 86400);
        return { leadtime };
    }
}

module.exports = new GhnCarrierClient();
