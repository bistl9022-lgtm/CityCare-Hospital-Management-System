package ui;

import dao.BaseDAO;
import model.Entity;
import utils.Field;
import utils.UIUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared skeleton of every data module (Patients, Doctors, Billing ...).
 *
 * A subclass only says WHAT its fields are, WHICH dao to use and HOW to build
 * its model object from the form. Layout, validation, table loading, and the
 * Add / Update / Delete / Search / View / Clear / Refresh buttons are all
 * implemented once, here.
 */
public abstract class CrudPanel<T extends Entity> extends JPanel implements Refreshable {

    protected final Field[] fields;
    protected final Map<String, JComponent> inputs = new LinkedHashMap<String, JComponent>();
    protected final DefaultTableModel tableModel;
    protected final JTable table;
    protected final JTextField searchBox = UIUtils.textField();
    protected final JLabel statusLabel = UIUtils.caption(" ");

    protected CrudPanel(String heading, String subtitle, Field[] fields) {
        this.fields = fields;

        setLayout(new BorderLayout(0, 14));
        setBorder(new EmptyBorder(18, 18, 18, 18));
        setBackground(UIUtils.BG);

        String[] cols = new String[fields.length];
        for (int i = 0; i < fields.length; i++) cols[i] = fields[i].label;

        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);

        add(buildFormCard(heading, subtitle), BorderLayout.NORTH);
        add(buildTableCard(), BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedRowIntoForm();
        });
    }

    // ==================================================== layout

    private JPanel buildFormCard(String heading, String subtitle) {
        JPanel card = UIUtils.card(null);

        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel h = UIUtils.label(heading, 17, Font.BOLD, UIUtils.TEXT);
        h.setAlignmentX(LEFT_ALIGNMENT);
        JLabel s = UIUtils.caption(subtitle);
        s.setAlignmentX(LEFT_ALIGNMENT);
        titleBox.add(h);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(s);

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchBar.setOpaque(false);
        searchBox.setPreferredSize(new Dimension(220, 34));
        JButton searchBtn = UIUtils.primary("Search", "search");
        searchBtn.addActionListener(e -> refresh(searchBox.getText()));
        searchBox.addActionListener(e -> refresh(searchBox.getText()));
        searchBar.add(searchBox);
        searchBar.add(searchBtn);

        head.add(titleBox, BorderLayout.WEST);
        head.add(searchBar, BorderLayout.EAST);
        head.setBorder(new EmptyBorder(0, 0, 14, 0));

        JPanel grid = new JPanel(new GridLayout(0, 4, 14, 10));
        grid.setOpaque(false);
        for (Field f : fields) grid.add(buildInput(f));

        card.add(head, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        card.add(buildButtonBar(), BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildInput(Field f) {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setOpaque(false);
        JLabel lab = UIUtils.label(f.label + (f.required ? " *" : ""), 12, Font.BOLD, UIUtils.MUTED);
        JComponent comp;
        if (f.type == Field.Type.COMBO) {
            comp = UIUtils.comboBox(f.options);
        } else {
            JTextField t = UIUtils.textField();
            if (f.type == Field.Type.DATE) t.setToolTipText("Format: yyyy-MM-dd");
            if (f.type == Field.Type.TIME) t.setToolTipText("Format: HH:mm (24 hour)");
            comp = t;
        }
        inputs.put(f.key, comp);
        box.add(lab, BorderLayout.NORTH);
        box.add(comp, BorderLayout.CENTER);
        return box;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(14, 0, 0, 0));

        JButton add = UIUtils.primary(addLabel(), "cross");
        JButton upd = UIUtils.accent("Update", null);
        JButton del = UIUtils.danger("Delete", null);
        JButton view = UIUtils.ghost("View", "eye");
        JButton clr = UIUtils.ghost("Clear", null);
        JButton ref = UIUtils.ghost("Refresh", "refresh");

        add.addActionListener(e -> onAdd());
        upd.addActionListener(e -> onUpdate());
        del.addActionListener(e -> onDelete());
        view.addActionListener(e -> onView());
        clr.addActionListener(e -> clearForm());
        ref.addActionListener(e -> { searchBox.setText(""); refresh(""); });

        bar.add(add); bar.add(upd); bar.add(del); bar.add(view); bar.add(clr); bar.add(ref);
        for (JButton b : extraButtons()) bar.add(b);
        return bar;
    }

    private JPanel buildTableCard() {
        JPanel card = UIUtils.card(null);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(0, 0, 10, 0));
        head.add(UIUtils.section("Records"), BorderLayout.WEST);
        head.add(statusLabel, BorderLayout.EAST);

        JScrollPane sp = UIUtils.styledTable(table);
        colourStatusColumns();

        card.add(head, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    /** Status / priority style columns are painted in their meaning colour. */
    private void colourStatusColumns() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                     boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setFont(UIUtils.bold(13));
                if (!sel) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 251, 252));
                    c.setForeground(UIUtils.statusColor(String.valueOf(v)));
                }
                return c;
            }
        };
        for (int i = 0; i < fields.length; i++) {
            String l = fields[i].label.toLowerCase();
            if (l.contains("status") || l.contains("priority") || l.contains("availability")) {
                table.getColumnModel().getColumn(i).setCellRenderer(r);
            }
        }
    }

    // ==================================================== form values

    public Map<String, String> getValues() {
        Map<String, String> v = new LinkedHashMap<String, String>();
        for (Field f : fields) {
            JComponent c = inputs.get(f.key);
            if (c instanceof JTextField) v.put(f.key, ((JTextField) c).getText().trim());
            else if (c instanceof JComboBox) {
                Object sel = ((JComboBox<?>) c).getSelectedItem();
                v.put(f.key, sel == null ? "" : sel.toString());
            }
        }
        return v;
    }

    public void setValue(String key, String value) {
        JComponent c = inputs.get(key);
        if (c instanceof JTextField) {
            ((JTextField) c).setText(value == null ? "" : value);
        } else if (c instanceof JComboBox) {
            JComboBox<?> box = (JComboBox<?>) c;
            String v = value == null ? "" : value;
            for (int i = 0; i < box.getItemCount(); i++) {
                String item = String.valueOf(box.getItemAt(i));
                // matches "P001" against the display value "P001 - Rahul Mishra" too
                if (item.equals(v) || item.startsWith(v + " - ")) { box.setSelectedIndex(i); return; }
            }
        }
    }

    public String getValue(String key) { return getValues().get(key); }

    public void clearForm() {
        for (Field f : fields) {
            JComponent c = inputs.get(f.key);
            if (c instanceof JTextField) ((JTextField) c).setText("");
            else if (c instanceof JComboBox && ((JComboBox<?>) c).getItemCount() > 0)
                ((JComboBox<?>) c).setSelectedIndex(0);
        }
        table.clearSelection();
        suggestId();
        afterClear();
    }

    /** Fills the id box with the next free id so users never type duplicates. */
    protected void suggestId() {
        if (idPrefix() == null) return;
        try {
            setValue(fields[0].key, dao().nextId(idPrefix(), 3));
        } catch (Exception ignored) { }
    }

    private void loadSelectedRowIntoForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        for (int i = 0; i < fields.length; i++) {
            Object v = tableModel.getValueAt(row, i);
            setValue(fields[i].key, v == null ? "" : String.valueOf(v));
        }
    }

    // ==================================================== actions

    protected void onAdd() {
        String problem = UIUtils.validate(fields, getValues());
        if (problem != null) { UIUtils.warn(this, problem); return; }
        try {
            T item = fromForm(getValues());
            if (dao().exists(item.id())) {
                UIUtils.warn(this, "A record with ID \"" + item.id() + "\" already exists.\n" +
                                   "Please use a different ID or press Update instead.");
                return;
            }
            dao().insert(item);
            refresh("");
            clearForm();
            UIUtils.info(this, "Record added successfully.");
        } catch (SQLException ex) {
            UIUtils.error(this, "Could not save the record.\n\n" + ex.getMessage());
        }
    }

    protected void onUpdate() {
        String problem = UIUtils.validate(fields, getValues());
        if (problem != null) { UIUtils.warn(this, problem); return; }
        try {
            T item = fromForm(getValues());
            if (!dao().exists(item.id())) {
                UIUtils.warn(this, "No record found with ID \"" + item.id() + "\".\n" +
                                   "Select a row from the table first.");
                return;
            }
            dao().update(item);
            refresh(searchBox.getText());
            UIUtils.info(this, "Record updated successfully.");
        } catch (SQLException ex) {
            UIUtils.error(this, "Could not update the record.\n\n" + ex.getMessage());
        }
    }

    protected void onDelete() {
        String id = getValue(fields[0].key);
        if (id == null || id.isEmpty()) {
            UIUtils.warn(this, "Select a record from the table before deleting.");
            return;
        }
        if (!UIUtils.confirm(this, "Delete record \"" + id + "\" permanently?")) return;
        try {
            if (!dao().exists(id)) { UIUtils.warn(this, "That record no longer exists."); return; }
            dao().delete(id);
            refresh(searchBox.getText());
            clearForm();
            UIUtils.info(this, "Record deleted.");
        } catch (SQLException ex) {
            UIUtils.error(this, "Could not delete the record.\n\n" + ex.getMessage());
        }
    }

    protected void onView() {
        int row = table.getSelectedRow();
        if (row < 0) { UIUtils.warn(this, "Select a row in the table to view its details."); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            Object v = tableModel.getValueAt(row, i);
            sb.append(String.format("%-22s: %s%n", fields[i].label, v == null ? "-" : v));
        }
        UIUtils.textDialog(this, "Record details", sb.toString());
    }

    /** Reloads the table, optionally filtered by a keyword. */
    public void refresh(String keyword) {
        try {
            List<T> data = (keyword == null || keyword.trim().isEmpty())
                    ? dao().findAll() : dao().search(keyword);
            setRows(data);
        } catch (SQLException ex) {
            UIUtils.error(this, "Could not load records.\n\n" + ex.getMessage());
        }
    }

    protected void setRows(List<T> data) {
        tableModel.setRowCount(0);
        for (T t : data) tableModel.addRow(t.toRow());
        statusLabel.setText(data.size() + " record" + (data.size() == 1 ? "" : "s") + " shown");
        afterRefresh();
    }

    // ==================================================== to implement

    protected abstract BaseDAO<T> dao();
    protected abstract T fromForm(Map<String, String> v);

    /** Caption of the insert button (Patients say "Add", Appointments "Schedule"). */
    protected String addLabel() { return "Add"; }

    /** Prefix used for auto-generated ids (e.g. "P"), or null to disable. */
    protected String idPrefix() { return null; }

    /** Extra module-specific buttons, e.g. "Low Stock" or "Generate Bill". */
    protected JButton[] extraButtons() { return new JButton[0]; }

    protected void afterRefresh() { }
    protected void afterClear()   { }

    /** Reloads the module whenever the user navigates back to it. */
    @Override public void reload() { refresh(searchBox.getText()); }
}
