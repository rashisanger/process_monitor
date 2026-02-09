🖥️ Process Management Dashboard (Java Swing)

A desktop-based Process Management Dashboard built using Java Swing, inspired by system task managers. The application allows users to monitor running processes, analyze CPU usage trends, search processes in real time, and terminate processes through an interactive GUI.

🚀 Features
🔍 Real-Time Process Search

Search processes by process name using a dynamic search bar.

Implements DocumentListener for instant filtering as the user types.

Improves usability and reduces manual scanning effort.

📊 CPU Usage Visualization

Custom-built CPU usage graph panel for visual analysis.

Displays real-time CPU trends for running processes.

Uses a dedicated CustomGraphPanel for clean separation of logic and UI.

📈 CPU Trend Analysis

Compares current CPU usage with previous snapshots.

Displays intuitive trend indicators:

↑↑ High – Sharp CPU increase

↑ – Moderate increase

≈ – Stable usage

↓ – Decrease

↓↓ High – Sharp decrease

Helps identify CPU-intensive or unstable processes quickly.

❌ Process Termination

Allows users to terminate selected processes directly from the table.

Adds basic process control functionality similar to an OS task manager.

🧩 Modular & Scalable Design

Clear separation of concerns:

Process data handling

UI components

Graph visualization

CPU trend calculation

Easy to extend with memory usage, disk I/O, or alerts.

🛠️ Tech Stack

Language: Java

GUI Framework: Java Swing

Visualization: Custom Swing-based graph panel

Architecture: Event-driven, modular design

📂 Key Components

JTable – Displays process details (PID, name, CPU usage, trend)

JTextField + DocumentListener – Real-time process search

CustomGraphPanel – CPU usage visualization

ActionListeners – Handle user interactions (search, terminate)

Trend Analysis Module – Compares CPU usage over time

🎯 Learning Outcomes

Hands-on experience with Java Swing GUI development

Implemented real-time filtering using event listeners

Built custom data visualizations

Strengthened understanding of process monitoring concepts

Improved skills in modular desktop application design
