const express = require("express");
const router = express.Router();
const shadeController = require("../controllers/shade.controller");
const { protect, authorize } = require("../middlewares/auth");

// ─── Public / Client Routes ───────────────────────────────────
router.get("/", shadeController.getShades);
router.post("/match", shadeController.findBestMatch);
router.get("/:shadeId", shadeController.getShadeById);

// ─── Admin Write Routes (require auth + admin role) ───────────
router.post("/", protect, authorize("admin"), shadeController.createShade);
router.put("/:shadeId", protect, authorize("admin"), shadeController.updateShade);
router.delete("/:shadeId", protect, authorize("admin"), shadeController.deleteShade);

module.exports = router;