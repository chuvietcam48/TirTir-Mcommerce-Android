# TirTir Project

**TirTir Foundation Finder** is a web application designed to help users find their perfect cushion foundation shade. The system utilizes a color analysis algorithm to match the user's skin tone with the extensive TirTir shade range.

---

## 🛠 Tech Stack

* **Frontend:** Angular, TypeScript, SCSS.
* **Backend:** Node.js, Express.js.
* **Database:** MongoDB Atlas (Cloud), Mongoose.

---

## 📋 Prerequisites

Before running the project, make sure you have the following installed:

1.  **Node.js** (v18+): [Download here](https://nodejs.org/)
2.  **Angular CLI**: Run `npm install -g @angular/cli`
3.  **Git**: [Download here](https://git-scm.com/)
4.  **MongoDB Compass** (Optional but recommended): [Download here](https://www.mongodb.com/products/tools/compass) - Used to view data visually.

---

## 🚀 Installation & Setup Guide

Follow these steps strictly to get the project running.

### Step 1: Clone the Repository

### Step 2: Backend Setup (Server)
1. Navigate to the backend folder:
cd backend
2. Install dependencies: This downloads all required libraries (Express, Mongoose, etc.).
npm install
3. Configure Environment Variables (.env) - ⚠️ IMPORTANT:
- Find the file named .env.example in the backend folder.
- Copy and paste it, then rename the copy to .env.
- Update the content: You need the MONGO_URI (Database Connection String). Ask the Team Leader for the connection string. DO NOT share this string publicly.
Your .env file should look like this:
PORT=5001
MONGO_URI=mongodb+srv://<username>:<password>@cluster0....mongodb.net/TirTir-Project

4. Start the Server:
npm run dev
-> Success: You should see Server running on port 5001 and MongoDB Connected.nn

### Step 3: Frontend Setup (Client)
- Keep the Backend terminal running. Open a new terminal window.
- Navigate to the frontend folder:
cd frontend, cd tirtir-frontend
- Install dependencies: This downloads Angular libraries.
npm install
- Start the Angular app:
ng serve
Open Browser: Go to http://localhost:4200 to see the application running.

☁️ Database Information (MongoDB Atlas)
We are using MongoDB Atlas, a cloud-based database. This ensures everyone on the team uses the exact same data.

❓ FAQ for Team Members:
1. Do I need to install MongoDB on my machine?

NO. You only need the connection string (URI) in your .env file. The database lives on the cloud.

2. Do I need to import products.json or shades.json manually?

NO. The Team Leader has already imported all necessary data to the Cloud. Once you connect, the data will appear automatically.

Warning: Do NOT drop (delete) the database or collections without team consensus.

3. Do I need MongoDB Compass?

It is Optional. You don't need it to run the app.

However, it is highly recommended if you want to verify if your code is saving data correctly. You can use the same connection string to log in to Compass and view the data tables.

🔗 API Endpoints (Quick Reference)
Health Check: http://localhost:5000/health

Get All Shades: http://localhost:5000/api/shades

Filter by Product: http://localhost:5000/api/shades?parentId=SA