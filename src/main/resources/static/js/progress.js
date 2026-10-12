/* PrepStart Progress: self-contained SVG charts, no CDN or npm dependencies. */
(() => {
    "use strict";
    const svg = document.getElementById("progressChart");
    const graphType = document.getElementById("chartType");
    const graphEmpty = document.getElementById("chartEmpty");
    const chartInfo = document.getElementById("chartInfo");
    const chartPeriod = document.getElementById("chartPeriod");
    const buttons = [...document.querySelectorAll("[data-range]")];
    if (!svg || !graphType || !graphEmpty || !chartInfo || !chartPeriod) return;

    const NS = "http://www.w3.org/2000/svg";
    let activeRange = "week";
    let currentData = null;
    let fetchSequence = 0;

    function element(tag, attributes = {}, text = null) {
        const node = document.createElementNS(NS, tag);
        for (const [name, value] of Object.entries(attributes)) node.setAttribute(name, String(value));
        if (text !== null) node.textContent = String(text);
        return node;
    }
    function append(parent, tag, attributes, text = null) {
        const node = element(tag, attributes, text);
        parent.appendChild(node);
        return node;
    }
    function yPosition(score) { return 288 - (score / 100) * 258; }
    function xPosition(index, size) { return size <= 1 ? 465 : 72 + index * (800 / (size - 1)); }
    function emptyState(show, heading = "No quiz scores for this period") {
        graphEmpty.hidden = !show;
        svg.hidden = show;
        if (show) graphEmpty.querySelector("h3").textContent = heading;
    }

    function draw() {
        if (!currentData) return;
        const { labels, scores, counts } = currentData;
        svg.replaceChildren();
        if (!scores.some(v => typeof v === "number" && Number.isFinite(v))) {
            emptyState(true);
            return;
        }
        emptyState(false);
        svg.setAttribute("aria-label", `Quiz performance (${currentData.rangeLabel}) as a ${graphType.value} chart`);

        const defs = append(svg, "defs", {});
        const gradient = append(defs, "linearGradient", { id: "scoreArea", x1: "0", y1: "0", x2: "0", y2: "1" });
        append(gradient, "stop", { offset: "0%", "stop-color": "#1976d2", "stop-opacity": "0.33" });
        append(gradient, "stop", { offset: "100%", "stop-color": "#1976d2", "stop-opacity": "0.02" });
        const plot = append(svg, "g", {});

        for (let score = 0; score <= 100; score += 20) {
            const y = yPosition(score);
            append(plot, "line", { x1: 60, y1: y, x2: 885, y2: y, stroke: "#e3eaf2", "stroke-width": 1, "stroke-dasharray": score === 0 ? "0" : "4 5" });
            append(plot, "text", { x: 48, y: y + 4, "font-size": 12, "text-anchor": "end", fill: "#8290a3" }, `${score}%`);
        }
        append(plot, "line", { x1: 60, y1: 30, x2: 60, y2: 288, stroke: "#ced9e5" });

        labels.forEach((label, i) => {
            append(plot, "text", { x: xPosition(i, labels.length), y: 318, "font-size": 12, "text-anchor": "middle", fill: "#76869b" }, label);
        });

        const items = scores.map((value, index) => ({
            x: xPosition(index, scores.length),
            y: value == null ? null : yPosition(Math.max(0, Math.min(100, Number(value)))),
            label: labels[index],
            value: value,
            count: counts[index]
        }));
        const active = items.filter(i => i.y !== null && Number.isFinite(i.y));

        if (graphType.value === "bar") {
            const width = Math.min(90, 640 / Math.max(1, labels.length));
            for (const item of active) {
                const bar = append(plot, "rect", { x: item.x - width / 2, y: item.y,
                    width, height: Math.max(0, 288 - item.y), rx: 5, fill: "#1976d2", tabindex: 0 });
                append(bar, "title", {}, `${item.label}: ${item.value}% average across ${item.count} quiz(es)`);
            }
        } else {
            // Break the line/area on dates without attempts. No misleading zero points.
            const groups = [];
            let currentGroup = [];
            for (const item of items) {
                if (item.y === null || !Number.isFinite(item.y)) {
                    if (currentGroup.length) groups.push(currentGroup);
                    currentGroup = [];
                } else {
                    currentGroup.push(item);
                }
            }
            if (currentGroup.length) groups.push(currentGroup);
            for (const group of groups) {
                const coords = group.map(p => `${p.x},${p.y}`).join(" ");
                if (graphType.value === "area" && group.length >= 2) {
                    append(plot, "polygon", { points: `${group[0].x},288 ${coords} ${group[group.length - 1].x},288`, fill: "url(#scoreArea)" });
                }
                if (group.length >= 2) {
                    append(plot, "polyline", { points: coords, fill: "none", stroke: "#1976d2", "stroke-width": 3, "stroke-linejoin": "round", "stroke-linecap": "round" });
                }
            }
            for (const item of active) {
                const circle = append(plot, "circle", { cx: item.x, cy: item.y, r: 6,
                    fill: "#1976d2", stroke: "#fff", "stroke-width": 2.5, tabindex: 0 });
                append(circle, "title", {}, `${item.label}: ${item.value}% average across ${item.count} quiz(es)`);
            }
        }
    }

    async function load(range) {
        const seq = ++fetchSequence;
        chartInfo.textContent = "Loading quiz scores...";
        try {
            const response = await fetch(`/progress/chart?range=${encodeURIComponent(range)}`, {
                headers: { Accept: "application/json" }, credentials: "same-origin"
            });
            if (!response.ok) throw new Error(`Server returned ${response.status}`);
            const data = await response.json();
            if (seq !== fetchSequence) return;
            currentData = data;
            chartPeriod.textContent = data.rangeLabel;
            chartInfo.textContent = `${data.recordedAttempts} scored quiz attempt(s) in this period`;
            draw();
        } catch (error) {
            if (seq !== fetchSequence) return;
            currentData = null;
            chartInfo.textContent = "Unable to load performance data";
            emptyState(true, "Could not load quiz history");
        }
    }

    buttons.forEach(button => button.addEventListener("click", () => {
        activeRange = button.dataset.range;
        buttons.forEach(item => {
            const selected = item === button;
            item.classList.toggle("active", selected);
            item.setAttribute("aria-pressed", String(selected));
        });
        load(activeRange);
    }));
    graphType.addEventListener("change", draw);
    load(activeRange);
})();
