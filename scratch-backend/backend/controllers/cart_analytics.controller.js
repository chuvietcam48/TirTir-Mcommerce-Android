const Assignment = require('../models/recovery_experiment_assignment.model');
const mongoose = require('mongoose');

// G1/G2 Analytics Pipeline
exports.getChiSquareInputs = async (req, res) => {
  try {
    const { experimentId } = req.params;
    if (!mongoose.Types.ObjectId.isValid(experimentId)) {
        return res.status(400).json({ error: "Invalid experiment ID" });
    }

    const expObjId = new mongoose.Types.ObjectId(experimentId);

    const pipeline = [
      { $match: { experiment_id: expObjId } },
      {
        $lookup: {
          from: 'cartrecoveryevents',
          let: { cartId: "$cart_id" },
          pipeline: [
              { $match: { $expr: { $and: [
                  { $eq: ["$cart_id", "$$cartId"] },
                  { $eq: ["$event_type", "cart_recovered"] }
              ]}}}
          ],
          as: "recoveryStatus"
        }
      },
      {
        $addFields: {
          is_recovered: { $cond: [{ $gt: [{ $size: "$recoveryStatus" }, 0] }, 1, 0] }
        }
      },
      {
        $group: {
          _id: "$variant_name",
          total_assigned: { $sum: 1 },
          total_recovered: { $sum: "$is_recovered" }
        }
      },
      {
        $project: {
          variant_name: "$_id",
          total_assigned: 1,
          total_recovered: 1,
          recovery_rate: { 
            $cond: [
              { $eq: ["$total_assigned", 0] }, 
              0, 
              { $multiply: [{ $divide: ["$total_recovered", "$total_assigned"] }, 100] }
            ]
          },
          _id: 0
        }
      }
    ];

    const results = await Assignment.aggregate(pipeline);
    return res.status(200).json(results);
  } catch (error) {
    console.error("Analytics Error: ", error);
    return res.status(500).json({ error: "Failed to generate analytics" });
  }
};
