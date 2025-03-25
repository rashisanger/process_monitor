import javax.swing.*;
 import javax.swing.table.DefaultTableModel;
 import java.awt.*;
 import java.awt.event.ActionEvent;
 import java.awt.event.ActionListener;
 import java.io.BufferedReader;
 import java.io.InputStreamReader;
 import java.lang.management.ManagementFactory;
 import com.sun.management.OperatingSystemMXBean;
 import java.util.ArrayList;
 import java.util.LinkedList;
 import java.util.List;
 
 public class ProcessMonitorDashboard {
     private JFrame frame;
     private JTable table;
     private DefaultTableModel tableModel;
     private OperatingSystemMXBean osBean;
     private Timer updateTimer;
     private LinkedList<Double> cpuHistory; // Store CPU usage history
     private CustomGraphPanel graphPanel;
 
     // Constructor
     public ProcessMonitorDashboard() {
         osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
         cpuHistory = new LinkedList<>();
         initializeUI();
         startUpdates();
     }
 
     // Module 1: Data Collection
     private List<ProcessInfo> getProcessData() {
         List<ProcessInfo> processes = new ArrayList<>();
         try {
             Process process = Runtime.getRuntime().exec(
                     System.getProperty("os.name").toLowerCase().contains("windows")
                             ? "tasklist" : "ps aux"
             );
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             String line;
             boolean firstLine = true;
 
             while ((line = reader.readLine()) != null) {
                 if (firstLine) {
                     firstLine = false;
                     continue;
                 }
                 String[] parts = line.trim().split("\\s+");
                 if (parts.length >= 5) {
                     try {
                         processes.add(new ProcessInfo(
                                 Integer.parseInt(parts[1]), // PID
                                 parts[0],                   // Name
                                 "Running",                 // State (simplified)
                                 Double.parseDouble(parts[2]), // CPU%
                                 Double.parseDouble(parts[3])  // Memory in MB
                         ));
                     } catch (NumberFormatException ignored) {}
                 }
             }
         } catch (Exception e) {
             System.out.println("Error fetching process data: " + e.getMessage());
         }
         return processes;
     }
 
     // Module 2: GUI Functions
     private void initializeUI() {
         frame = new JFrame("Real-Time Process Monitoring Dashboard");
         frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         frame.setSize(800, 600);
         frame.setLayout(new BorderLayout());
 
         // Process Table
         String[] columns = {"PID", "Name", "State", "CPU%", "Memory (MB)"};
         tableModel = new DefaultTableModel(columns, 0);
         table = new JTable(tableModel);
         JScrollPane scrollPane = new JScrollPane(table);
         frame.add(scrollPane, BorderLayout.CENTER);
 
         // Buttons
         JPanel buttonPanel = new JPanel();
         JButton refreshButton = new JButton("Refresh Now");
         JButton terminateButton = new JButton("Terminate Process");
 
         refreshButton.addActionListener(e -> updateTable());
         terminateButton.addActionListener(e -> terminateProcess());
 
         buttonPanel.add(refreshButton);
         buttonPanel.add(terminateButton);
         frame.add(buttonPanel, BorderLayout.SOUTH);
 
         // CPU Graph (Module 3: Visualization)
         graphPanel = new CustomGraphPanel();
         graphPanel.setPreferredSize(new Dimension(700, 300));
         frame.add(graphPanel, BorderLayout.NORTH);
 
         frame.setVisible(true);
     }
 
     private void updateTable() {
         tableModel.setRowCount(0); // Clear table
         List<ProcessInfo> processes = getProcessData();
         for (ProcessInfo proc : processes) {
             tableModel.addRow(new Object[]{
                     proc.pid,
                     proc.name,
                     proc.state,
                     String.format("%.1f%%", proc.cpu),
                     String.format("%.1f MB", proc.memory)
             });
         }
     }
 
     private void terminateProcess() {
         int selectedRow = table.getSelectedRow();
         if (selectedRow >= 0) {
             int pid = (Integer) tableModel.getValueAt(selectedRow, 0);
             try {
                 String command = System.getProperty("os.name").toLowerCase().contains("windows")
                         ? "taskkill /PID " + pid + " /F"
                         : "kill -9 " + pid;
                 Runtime.getRuntime().exec(command);
                 updateTable();
             } catch (Exception e) {
                 System.out.println("Error terminating process " + pid + ": " + e.getMessage());
             }
         }
     }
 
     // Module 3: Data Visualization
     private void updateCpuGraph() {
         double cpuUsage = osBean.getSystemCpuLoad() * 100;
         if (cpuUsage < 0) cpuUsage = 0; // getSystemCpuLoad might return -1 if not available yet
         cpuHistory.add(cpuUsage);
         if (cpuHistory.size() > 20) { // Limit to 20 points
             cpuHistory.removeFirst();
         }
         graphPanel.repaint(); // Redraw the graph
     }
 
     private void startUpdates() {
         updateTable(); // Initial update
 
         // Timer for continuous updates
         updateTimer = new Timer(1000, new ActionListener() {
             @Override
             public void actionPerformed(ActionEvent e) {
                 updateCpuGraph();
                 if (System.currentTimeMillis() % 5000 == 0) { // Every 5 seconds
                     updateTable();
                 }
             }
         });
         updateTimer.start();
     }
 
     // Custom Graph Panel
     private class CustomGraphPanel extends JPanel {
         @Override
         protected void paintComponent(Graphics g) {
             super.paintComponent(g);
             Graphics2D g2d = (Graphics2D) g;
 
             // Background
             g2d.setColor(Color.WHITE);
             g2d.fillRect(0, 0, getWidth(), getHeight());
 
             // Title
             g2d.setColor(Color.BLACK);
             g2d.drawString("CPU Usage Over Time (%)", getWidth() / 2 - 50, 20);
 
             // Axes
             g2d.drawLine(50, 250, 650, 250); // X-axis
             g2d.drawLine(50, 50, 50, 250);    // Y-axis
 
             // Labels
             g2d.drawString("0%", 30, 250);
             g2d.drawString("100%", 25, 50);
 
             // Draw CPU usage line
             if (cpuHistory.size() > 1) {
                 g2d.setColor(Color.BLUE);
                 int xStep = (600 / (cpuHistory.size() - 1));
                 for (int i = 0; i < cpuHistory.size() - 1; i++) {
                     int x1 = 50 + i * xStep;
                     int y1 = 250 - (int) (cpuHistory.get(i) * 2); // Scale 0-100 to 0-200 pixels
                     int x2 = 50 + (i + 1) * xStep;
                     int y2 = 250 - (int) (cpuHistory.get(i + 1) * 2);
                     g2d.drawLine(x1, y1, x2, y2);
                 }
             }
         }
     }
 
     // Process Info Class
     private static class ProcessInfo {
         int pid;
         String name;
         String state;
         double cpu;
         double memory;
 
         ProcessInfo(int pid, String name, String state, double cpu, double memory) {
             this.pid = pid;
             this.name = name;
             this.state = state;
             this.cpu = cpu;
             this.memory = memory;
         }
     }
 
     public static void main(String[] args) {
         SwingUtilities.invokeLater(() -> new ProcessMonitorDashboard());
     }
 }