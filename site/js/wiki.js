// wiki の探す箱 (js/wiki-search.json。名前の一部で、全部の頁から探す) と、頁の中の絞り込み (言葉と等級)
(function () {
  var q = document.getElementById("wiki-q");
  var box = document.getElementById("wiki-results");
  var index = null;
  function load() {
    if (index) return Promise.resolve(index);
    return fetch("js/wiki-search.json").then(function (r) { return r.json(); }).then(function (d) { index = d; return d; });
  }
  function norm(s) {
    // ひらがなとカタカナを同じに見る
    return (s || "").toLowerCase().replace(/[ァ-ヶ]/g, function (c) { return String.fromCharCode(c.charCodeAt(0) - 0x60); });
  }
  function render(list, word) {
    box.innerHTML = "";
    if (!word) { box.hidden = true; return; }
    if (!list.length) {
      var p = document.createElement("p");
      p.className = "none";
      p.textContent = "「" + word + "」は見つかりませんでした";
      box.appendChild(p);
    }
    list.slice(0, 40).forEach(function (e) {
      var a = document.createElement("a");
      a.href = e.u;
      a.setAttribute("role", "option");
      var b = document.createElement("b");
      b.textContent = e.n;
      var s = document.createElement("span");
      s.textContent = e.k;
      a.appendChild(b);
      a.appendChild(s);
      box.appendChild(a);
    });
    box.hidden = false;
  }
  if (q && box) {
    q.addEventListener("focus", load);
    q.addEventListener("input", function () {
      var word = q.value.trim();
      load().then(function (d) {
        var w = norm(word);
        var hits = word ? d.filter(function (e) { return norm(e.n).indexOf(w) >= 0; }) : [];
        hits.sort(function (a, b) { return norm(a.n).indexOf(w) - norm(b.n).indexOf(w) || a.n.length - b.n.length; });
        render(hits, word);
      });
    });
    q.addEventListener("keydown", function (e) {
      if (e.key === "Enter") { var first = box.querySelector("a"); if (first) location.href = first.href; }
      if (e.key === "Escape") { q.value = ""; render([], ""); }
    });
    document.addEventListener("click", function (e) { if (!box.contains(e.target) && e.target !== q) box.hidden = true; });
  }

  // 頁の中の絞り込み: data-name / data-text を持つ札・行を、言葉と等級で隠す
  var text = document.getElementById("f-text");
  if (!text) return;
  var tiers = Array.prototype.slice.call(document.querySelectorAll(".f-tier"));
  var count = document.querySelector(".f-count");
  var rows = Array.prototype.slice.call(document.querySelectorAll("main [data-text]"));
  function apply() {
    var w = norm(text.value.trim());
    var on = tiers.filter(function (t) { return t.getAttribute("aria-pressed") === "true"; }).map(function (t) { return t.getAttribute("data-tier"); });
    var shown = 0;
    rows.forEach(function (r) {
      var ok = (!w || norm(r.getAttribute("data-text")).indexOf(w) >= 0) && (!on.length || on.indexOf(r.getAttribute("data-tier")) >= 0);
      r.hidden = !ok;
      if (ok) shown++;
    });
    document.querySelectorAll(".armor-set, .wiki-sec").forEach(function (s) {
      var any = s.querySelector("[data-text]:not([hidden])") || !s.querySelector("[data-text]");
      s.hidden = !any;
    });
    if (count) count.textContent = (w || on.length) ? shown + " 件" : "";
  }
  text.addEventListener("input", apply);
  tiers.forEach(function (t) {
    t.addEventListener("click", function () {
      t.setAttribute("aria-pressed", t.getAttribute("aria-pressed") === "true" ? "false" : "true");
      apply();
    });
  });
})();
