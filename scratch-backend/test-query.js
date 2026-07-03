const mongoose = require('mongoose');
const Product = require('./models/Product');

mongoose.connect('mongodb://localhost:27017/tirtir', {
  useNewUrlParser: true,
  useUnifiedTopology: true
}).then(async () => {
    console.log("Connected to MongoDB.");

    const query1 = {
        $and: [
            { Name: { $regex: 'cushion', $options: 'i' } }
        ]
    };

    const cushion = await Product.findOne(query1);
    console.log("Found Cushion:", cushion ? cushion.Name : "None");

    const query2 = {
        $and: [
            {
                $or: [
                    { Category: { $regex: 'Serum|Ampoule', $options: 'i' } },
                    { Name: { $regex: 'Serum|Ampoule', $options: 'i' } }
                ]
            },
            { Category: { $not: /makeup|cushion|foundation|concealer/i } },
            { Name: { $not: /cushion|foundation|concealer|setting spray/i } }
        ]
    };

    const prod = await Product.findOne(query2);
    console.log("Found Prod with $not:", prod ? prod.Name : "None");

    mongoose.disconnect();
});
