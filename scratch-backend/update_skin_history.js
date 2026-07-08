const mongoose = require('mongoose');
const User = require('./models/User');
require('dotenv').config();

mongoose.connect(process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/tirtir', {
  useNewUrlParser: true,
  useUnifiedTopology: true,
}).then(async () => {
  const users = await User.find({});
  for (let user of users) {
    if (!user.skinProfile || !user.skinProfile.skinShade) {
      user.skinProfile = {
        skinShade: '21N',
        skinHex: '#E2C2A4',
        skinType: ['Combination', 'Sensitive'],
        insights: [
          { trait: 'Tone', value: 'Light Medium' },
          { trait: 'Undertone', value: 'Neutral' },
          { trait: 'ITA', value: '45.2°' },
          { trait: 'Texture', value: 'Smooth' },
          { trait: 'Pores', value: 'Normal' },
          { trait: 'Hydration', value: 'Well Hydrated' }
        ]
      };
      await user.save();
    }
  }
  console.log('Successfully injected mock skin scan data!');
  process.exit(0);
}).catch(console.error);
