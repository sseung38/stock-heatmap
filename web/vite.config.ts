import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The Spring Boot mock API (backend/local) serves these in development.
    proxy: {
      "/api": "http://localhost:8787",
      "/config.json": "http://localhost:8787",
    },
  },
  test: {
    environment: "jsdom",
  },
});
