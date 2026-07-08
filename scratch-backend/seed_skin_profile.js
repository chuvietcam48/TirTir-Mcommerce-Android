require('dotenv').config();
const mongoose = require('mongoose');
const User = require('./models/User');

async function seedSkinProfile() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log('Connected to DB');

    const email = 'cee.m48@gmail.com';
    const user = await User.findOne({ email });

    if (!user) {
      console.log(`User ${email} not found.`);
      process.exit(1);
    }

    // Injecting a rich skin profile so the user can test the Skin Scan History UI
    user.skinProfile = {
      skinTone: 'Fair',
      undertone: 'Warm',
      skinHex: '#FAD6C3',
      ITA_category: 'Light',
      texture: 'Smooth',
      pores: 'Small',
      hydration: 'Well Hydrated',
      skinType: 'Combination',
      concerns: ['Mild Redness', 'Occasional Breakouts'],
      recommendations: [
        'Use a gentle hydrating cleanser daily.',
        'Apply niacinamide serum to reduce redness.',
        'Always wear SPF 50+ sunscreen during the day.'
      ],
      confidence: 94.5,
      lastAnalyzedAt: new Date()
    };

    await user.save();
    console.log(`✅ Successfully seeded skin profile for ${email}`);
    process.exit(0);

  } catch (error) {
    console.error('Error:', error);
    process.exit(1);
  }
}

seedSkinProfile();
