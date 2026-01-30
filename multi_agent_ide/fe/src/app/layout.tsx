import type { ReactNode } from "react";
import "./globals.css";

export const metadata = {
  title: "Agent Graph UI",
  description: "Visualize multi-agent workflows and event streams.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
