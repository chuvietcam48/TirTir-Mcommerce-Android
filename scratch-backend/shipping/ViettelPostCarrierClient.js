class ViettelPostCarrierClient {
    async getAvailableServices(fromDistrict, toDistrict) {
        return [
            { service_id: 'VCN', short_name: 'Viettel Post - Chuyển phát nhanh (VCN)' },
            { service_id: 'VTK', short_name: 'Viettel Post - Chuyển phát tiết kiệm (VTK)' }
        ];
    }

    async calculateFee(serviceId, fromDistrict, toDistrict, toWardCode, weight, length, width, height, orderValue) {
        if (serviceId === 'VCN') return { total: 35000 };
        return { total: 22000 };
    }

    async getLeadTime(fromDistrict, fromWardCode, toDistrict, toWardCode, serviceId) {
        const days = serviceId === 'VCN' ? 2 : 5;
        const leadtime = Math.floor(Date.now() / 1000) + (days * 86400);
        return { leadtime };
    }
}

module.exports = new ViettelPostCarrierClient();
