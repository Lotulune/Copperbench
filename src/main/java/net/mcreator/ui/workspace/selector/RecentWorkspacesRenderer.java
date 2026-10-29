/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.workspace.selector;

import dev.copperbench.shell.ProductTheme;
import net.mcreator.generator.GeneratorFlavor;
import javax.swing.*;
import java.awt.*;

/** Names stay primary; paths and core versions are horizontal metadata. */
class RecentWorkspacesRenderer extends JPanel implements ListCellRenderer<RecentWorkspaceEntry> {
    private final JLabel name = new JLabel();
    private final JLabel path = new JLabel();
    private final JLabel version = new JLabel();
    private final JLabel icon = new JLabel();

    RecentWorkspacesRenderer() {
        super(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
        path.setFont(path.getFont().deriveFont(11f));
        version.setFont(version.getFont().deriveFont(10f));
        JPanel copy = new JPanel(new GridLayout(0, 1, 0, 3));
        copy.setOpaque(false);
        copy.add(name); copy.add(path); copy.add(version);
        add(icon, BorderLayout.WEST); add(copy, BorderLayout.CENTER);
    }

    @Override public Component getListCellRendererComponent(JList<? extends RecentWorkspaceEntry> list,
            RecentWorkspaceEntry entry, int index, boolean selected, boolean focused) {
        Color foreground = selected ? list.getSelectionForeground() : UIManager.getColor("Label.foreground");
        setBackground(selected ? list.getSelectionBackground() : list.getBackground());
        name.setForeground(foreground);
        path.setForeground(selected ? foreground : ProductTheme.muted());
        version.setForeground(path.getForeground());
        name.setText(entry.getName());
        String location = entry.getPath().getParentFile().getAbsolutePath();
        path.setText(location);
        version.setText(entry.getMCRVersion() == null ? "" : "MCreator " + entry.getMCRVersion());
        icon.setForeground(path.getForeground());
        icon.setIcon(entry.getType() == GeneratorFlavor.UNKNOWN ? null : ProductTheme.monochrome(entry.getType().getIcon()));
        setToolTipText(entry.getName() + " — " + location);
        return this;
    }
}
