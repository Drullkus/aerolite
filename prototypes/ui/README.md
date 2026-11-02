# UI mocks

HTML mocks of the ImGui panel of the preview for Aerolite 0.4.0.

## Open a page

- Open a page from the file system in a browser, for example `open prototypes/ui/variation-row.html` on macOS.
- The pages need no build step and no network.
- The pages share `imgui.css` and `imgui.js`. Keep the files in one folder.
- `provider-fields-d.html` also loads `providers.js`. That file holds the provider values, JSON, summaries and editor.
- The slider Panel width sets the panel width from 240 to 600 ImGui units.
- One CSS pixel is one ImGui unit.
- The checkbox Outline clipped items draws a red dashed outline on each item that passes the content edge or clips its text.
- The line under the controls lists the clipped items at the current width.

## The look

- `imgui.css` copies the colors of `ImGui.styleColorsDark` and the default ImGui sizes.
- The panel font is 13 units at the font scale 1.5. The mock uses a monospace font with the advance of ProggyClean at that size.
- The variation box is the default ImGui blue at 25% opacity, padded by the frame padding.
- Labels follow the Title Case of the lang file.
- The clipboard buttons read Copy and Paste. Their tooltips say export and import.

## variation-row.html

- The page shows the dimension header Overworld with two variation rows in the group Pillar Variations.
- Each row shows, from top to bottom: the five sliders, the combo Pattern and the widgets of the pattern, the combo Shape, the block fields and the buttons.
- The decay threshold slider tops out at 95%. The erosion threshold stays at least 5% above it.
- The Tiles widgets turn the X stagger entry off while Encased is on. Large tiles have no X entry.
- The Chaos sliders hold placeholder ranges. Their tooltips name the ranges.
- The shape Bridge adds the block field Trusses.
- A block field shows up to 8 matches of a list of 30 blocks. A colored square stands in for each item icon.
- The JSON under the panel shows each variation as Copy writes it.
- Each block field writes a simple provider object: `{"type": "minecraft:simple_state_provider", "state": {...}}`.
- Paste reads a simple provider object or a bare block state of the 0.3 format.
- The page starts with the narrow variant. The owner accepted it for the width of 240 units.
- The narrow variant has a half indent under Pattern, no indent under Surface paths, Strength, short Seam entries, the label Surface and the button Remove.
- The checkbox Wide variant shows the first labels and indents.
- `images/variation-row.png` shows the page at 360 units.
- `images/variation-row-240.png` shows the deepest widgets of the wide variant at 240 units, with the clip outline.
- `images/variation-row-240-narrow.png` shows the same state in the narrow variant.

## provider-fields.html

- The page shows one field design per block state provider type of Minecraft 1.21.1.
- Each box holds the field Wall with one provider type and the field Path with a simple state.
- A closed field shows the arrow button and one summary line.
- A closed simple state keeps its text field on the line.
- The arrow opens the editor. The editor starts with the combo Type.
- The text under each box is the JSON of the field Wall.
- Option A opens the editor inline below the field.
- Option B opens the editor in a window over the panel.
- Option C opens the editor in a tab of the variation box.
- The source of a randomized int provider is a full provider in the codec. The mock shows a simple state.
- `images/provider-fields.png` shows the page at 360 units.

## provider-fields-d.html

- The page shows option D, the corrected tab design of option C.
- Each box holds a tab bar. The tab Blocks holds the fields Wall and Path.
- The field Wall holds one provider type per box. The field Path holds a simple state.
- The arrow button of a field opens the tab of that field.
- When the tab of a field is open, its arrow button selects that tab.
- The arrow button never closes a tab.
- The cross on a tab is the only control that closes the tab.
- A closed tab gives the selection to the tab on its left.
- A click on the summary of a field that is not a simple state opens or selects its tab.
- A simple state keeps its text field and its dropdown. A click in that field opens no tab.
- The Blocks tab looks the same with any set of open tabs.
- New tabs go to the right end of the tab bar.
- The text under each box is the JSON of Wall and Path, as the variation codec writes it.
- The page starts with the tab Wall open and selected in each box.
- `images/provider-fields-d.png` shows the page at 360 units with the tab Wall selected.
- `images/provider-fields-d-blocks.png` shows the same page with the tab Blocks selected.
