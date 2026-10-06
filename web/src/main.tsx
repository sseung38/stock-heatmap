import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AuthGate } from "./auth/AuthGate";
import { loadConfig } from "./config";
import "./styles.css";

const config = await loadConfig();
const queryClient = new QueryClient();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthGate config={config}>
        <App />
      </AuthGate>
    </QueryClientProvider>
  </StrictMode>,
);
