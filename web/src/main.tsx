import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ApiProvider } from "./api/ApiProvider";
import { loadConfig } from "./config";
import "./styles.css";

const config = await loadConfig();
const queryClient = new QueryClient();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ApiProvider config={config} token={null}>
        <p>Stock Heatmap</p>
      </ApiProvider>
    </QueryClientProvider>
  </StrictMode>,
);
