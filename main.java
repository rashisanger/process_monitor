import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
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
    private TableRowSorter<DefaultTableModel> sorter;
    private OperatingSystemMXBean osBean;
    private Timer updateTimer;
    private LinkedList<Double> cpuHistory; // Store CPU usage history
    private CustomGraphPanel graphPanel;
    private JTextField searchField;
    private long totalMemory;

    // Constructor
    public ProcessMonitorDashboard() {
        osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        totalMemory = osBean.getTotalPhysicalMemorySize() / (1024 * 1024);
        cpuHistory = new LinkedList<>();
        initializeUI();
        startUpdates();
    }

    // Module 1: Data Collection
    private List<ProcessInfo> getProcessData() {
        List<ProcessInfo> processes = new ArrayList<>();
        String osName = System.getProperty("os.name").toLowerCase();
        try {
            String command = osName.contains("windows") ? "tasklist /FO CSV /V" : "ps -eo pid,comm,stat,%cpu,%mem,pri";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] parts = osName.contains("windows") ? parseCSVLine(line) : line.trim().split("\\s+");
                if (parts.length >= (osName.contains("windows") ? 9 : 6)) {
                    try {
                        int pid = Integer.parseInt(parts[1].replace("\"", ""));
                        String name = parts[0].replace("\"", "").toLowerCase().endsWith(".exe") 
                            ? parts[0].replace("\"", "").substring(0, parts[0].length() - 6) : parts[0].replace("\"", "");
                        String state = osName.contains("windows") ? parts[7].replace("\"", "") : parts[2];
                        double cpu = osName.contains("windows") ? estimateWindowsCpu(pid) : Double.parseDouble(parts[3]);
                        double memory = osName.contains("windows") 
                            ? Double.parseDouble(parts[4].replace("\"", "").replace(",", "").replace(" K", "")) / 1024.0 
                            : Double.parseDouble(parts[4]) * totalMemory / 100.0;
                        int priority = osName.contains("windows") ? parseWindowsPriority(parts[8].replace("\"", "")) : Integer.parseInt(parts[5]);
                        processes.add(new ProcessInfo(pid, name, state, cpu, memory, priority));
                    } catch (Exception ignored) {}
                }
            }
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error fetching process data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return processes;
    }

    // Estimate CPU usage for Windows (basic approximation)
    private double estimateWindowsCpu(int pid) {
        try {
            Process p = Runtime.getRuntime().exec("wmic path Win32_PerfFormattedData_PerfProc_Process get IDProcess,PercentProcessorTime");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(String.valueOf(pid))) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) return Double.parseDouble(parts[1]);
                }
            }
            reader.close();
        } catch (Exception ignored) {}
        return 0.0; // Fallback if WMIC fails
    }

    // Parse Windows priority levels
    private int parseWindowsPriority(String priorityStr) {
        return switch (priorityStr.toLowerCase()) {
            case "realtime" -> 24;
            case "high" -> 13;
            case "above normal" -> 8;
            case "normal" -> 0;
            case "below normal" -> -8;
            case "low" -> -15;
            default -> 0;
        };
    }

    // Parse CSV line for Windows tasklist output
    private String[] parseCSVLine(String line) {
        return line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
    }

    // Module 2: GUI Functions
    private void initializeUI() {
        frame = new JFrame("Real-Time Process Monitoring Dashboard");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Process Table
        String[] columns = {"PID", "Name", "State", "CPU%", "Memory (MB)", "Memory %", "Priority", "CPU Trend"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 || columnIndex == 6 ? Integer.class : String.class; // PID and Priority as integers
            }
        };
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Control Panel
        JPanel controlPanel = new JPanel(new BorderLayout());

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh Now");
        JButton terminateButton = new JButton("Terminate Process");
        JButton restartButton = new JButton("Restart Process"); // New feature
        JButton sortCpuButton = new JButton("Sort by CPU");
        JButton sortMemButton = new JButton("Sort by Memory");

        refreshButton.addActionListener(e -> updateTable());
        terminateButton.addActionListener(e -> terminateProcess());
        restartButton.addActionListener(e -> restartProcess());
        sortCpuButton.addActionListener(e -> sortTableByCPU());
        sortMemButton.addActionListener(e -> sortTableByMemory());

        buttonPanel.add(refreshButton);
        buttonPanel.add(terminateButton);
        buttonPanel.add(restartButton);
        buttonPanel.add(sortCpuButton);
        buttonPanel.add(sortMemButton);

        // Search Field
        searchField = new JTextField(20);
        searchField.setToolTipText("Search by process name (real-time)");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
        });
        JLabel searchLabel = new JLabel("Search: ");
        JPanel searchPanel = new JPanel();
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);

        controlPanel.add(searchPanel, BorderLayout.NORTH);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);
        frame.add(controlPanel, BorderLayout.SOUTH);

        graphPanel = new CustomGraphPanel();
        graphPanel.setPreferredSize(new Dimension(950, 300));
        frame.add(graphPanel, BorderLayout.NORTH);

        frame.setVisible(true);
    }

    private void updateTable() {
        List<ProcessInfo> prevProcesses = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            prevProcesses.add(new ProcessInfo(
                    (Integer) tableModel.getValueAt(i, 0),
                    (String) tableModel.getValueAt(i, 1),
                    (String) tableModel.getValueAt(i, 2),
                    Double.parseDouble(((String) tableModel.getValueAt(i, 3)).replace("%", "")),
                    Double.parseDouble(((String) tableModel.getValueAt(i, 4)).replace(" MB", "")),
                    (Integer) tableModel.getValueAt(i, 6)
            ));
        }

        tableModel.setRowCount(0);
        List<ProcessInfo> processes = getProcessData();
        for (ProcessInfo proc : processes) {
            double memoryPercent = (proc.memory / totalMemory) * 100;
            String cpuTrend = calculateCpuTrend(proc, prevProcesses);
            tableModel.addRow(new Object[]{
                    proc.pid,
                    proc.name,
                    proc.state,
                    String.format("%.1f%%", proc.cpu),
                    String.format("%.1f MB", proc.memory),
                    String.format("%.1f%%", memoryPercent),
                    proc.priority,
                    cpuTrend
            });
        }
    }

    private String calculateCpuTrend(ProcessInfo current, List<ProcessInfo> previous) {
        for (ProcessInfo prev : previous) {
            if (prev.pid == current.pid) {
                double diff = current.cpu - prev.cpu;
                if (diff > 10) return "↑↑ High";
                if (diff > 2) return "↑";
                if (diff < -10) return "↓↓ High";
                if (diff < -2) return "↓";
                return "≈";
            }
        }
        return "New";
    }
    private void terminateProcess() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            int pid = (Integer) tableModel.getValueAt(table.convertRowIndexToModel(selectedRow), 0);
            String name = (String) tableModel.getValueAt(table.convertRowIndexToModel(selectedRow), 1);
            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Are you sure you want to terminate " + name + " (PID: " + pid + ")?",
                    "Confirm Termination", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    String command = System.getProperty("os.name").toLowerCase().contains("windows")
                            ? "taskkill /PID " + pid + " /F"
                            : "kill -9 " + pid;
                    Process p = Runtime.getRuntime().exec(command);
                    p.waitFor();
                    if (p.exitValue() == 0) {
                        JOptionPane.showMessageDialog(frame, "Process " + pid + " terminated.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(frame, "Failed to terminate process " + pid + ".", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    updateTable();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Error terminating process " + pid + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(frame, "Please select a process to terminate.", "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void restartProcess() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            int pid = (Integer) tableModel.getValueAt(table.convertRowIndexToModel(selectedRow), 0);
            String name = (String) tableModel.getValueAt(table.convertRowIndexToModel(selectedRow), 1);
            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Restart " + name + " (PID: " + pid + ")? This will terminate and attempt to relaunch.",
                    "Confirm Restart", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    String killCommand = os.contains("windows") ? "taskkill /PID " + pid + " /F" : "kill -9 " + pid;
                    Process killProc = Runtime.getRuntime().exec(killCommand);
                    killProc.waitFor();

                    // Basic restart attempt (assumes process is an executable in PATH)
                    String restartCommand = os.contains("windows") ? "start " + name : name;
                    Runtime.getRuntime().exec(restartCommand);
                    JOptionPane.showMessageDialog(frame, "Restart attempt initiated for " + name + ".", "Success", JOptionPane.INFORMATION_MESSAGE);
                    updateTable();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Error restarting process " + pid + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(frame, "Please select a process to restart.", "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void sortTableByCPU() {
        sorter.setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.DESCENDING)));
        sorter.sort();
    }

    private void sortTableByMemory() {
        sorter.setSortKeys(List.of(new RowSorter.SortKey(4, SortOrder.DESCENDING)));
        sorter.sort();
    }

    private void filterTable() {
        String searchText = searchField.getText().trim().toLowerCase();
        sorter.setRowFilter(searchText.isEmpty() ? null : RowFilter.regexFilter("(?i)" + searchText, 1));
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
        updateTable();
        updateTimer = new Timer(1000, e -> {
            updateCpuGraph();
            if (System.currentTimeMillis() % 5000 == 0) {
                updateTable();
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
            int leftMargin = 50, bottomMargin = 250, graphWidth = 850, graphHeight = 200;
            g2d.drawLine(leftMargin, bottomMargin, leftMargin + graphWidth, bottomMargin);
            g2d.drawLine(leftMargin, bottomMargin - graphHeight, leftMargin, bottomMargin);

            // Labels
           g2d.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i <= 100; i += 20) {
                int y = bottomMargin - (i * graphHeight / 100);
                g2d.drawLine(leftMargin, y, leftMargin + graphWidth, y);
                g2d.setColor(Color.BLACK);
                g2d.drawString(i + "%", leftMargin - 30, y + 5);
                g2d.setColor(Color.LIGHT_GRAY);
            }

            // Draw CPU usage line
            if (cpuHistory.size() > 1) {
                g2d.setColor(Color.BLUE);
                int xStep = graphWidth / (cpuHistory.size() - 1);
                for (int i = 0; i < cpuHistory.size() - 1; i++) {
                    int x1 = leftMargin + i * xStep;
                    int y1 = bottomMargin - (int) (cpuHistory.get(i) * graphHeight / 100);
                    int x2 = leftMargin + (i + 1) * xStep;
                    int y2 = bottomMargin - (int) (cpuHistory.get(i + 1) * graphHeight / 100);
                    g2d.drawLine(x1, y1, x2, y2);
                }
            }

            // Legend
            g2d.setColor(Color.BLUE);
            g2d.fillRect(leftMargin + 10, 30, 10, 10);
            g2d.setColor(Color.BLACK);
            g2d.drawString("System CPU Usage", leftMargin + 25, 40);
        }
    }

    // Process Info Class
    private static class ProcessInfo {
        int pid;
        String name;
        String state;
        double cpu;
        double memory;
        int priority;

        ProcessInfo(int pid, String name, String state, double cpu, double memory, int priority) {
            this.pid = pid;
            this.name = name;
            this.state = state;
            this.cpu = cpu;
            this.memory = memory;
            this.priority = priority;
        }
    }

    public static void main(String[] args) {
       SwingUtilities.invokeLater(ProcessMonitorDashboard::new);
    }
}
