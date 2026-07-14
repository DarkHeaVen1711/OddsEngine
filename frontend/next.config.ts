import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Enable standalone output for Docker — produces a self-contained server.js bundle
  output: "standalone",
};

export default nextConfig;
