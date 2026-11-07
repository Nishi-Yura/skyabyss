// スキルツリーを試す (skill-tree.html)。木と決まりは tools/site/planner.py が埋めた JSON (ゲームの SkillData.canTake と同じ)
(function () {
  "use strict";
  var el = document.getElementById("pl-data");
  if (!el) return;
  var D = JSON.parse(el.textContent);
  var CELL = 64, ICON = 40;
  var state = { job: D.jobs[0].id, lv: D.maxLevel, taken: {} };
  var board = document.getElementById("pl-board");
  var sum = document.getElementById("pl-sum");
  var info = document.getElementById("pl-info");
  var lvIn = document.getElementById("pl-lv");
  var lvN = document.getElementById("pl-lv-n");

  function job() { for (var i = 0; i < D.jobs.length; i++) if (D.jobs[i].id === state.job) return D.jobs[i]; return D.jobs[0]; }
  function byId(j) { var m = {}; j.nodes.forEach(function (n) { m[n.id] = n; }); return m; }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }

  // ---------------------------------------------------------------- 決まり (SkillData.canTake と同じ順)
  function spent(t, j) { var s = 0; j.nodes.forEach(function (n) { if (t[n.id]) s += n.c; }); return s; }
  function archPoints(t, j, a) { var s = 0; j.nodes.forEach(function (n) { if (t[n.id] && n.a === a) s += n.c; }); return s; }
  function actives(t, j) { var s = 0; j.nodes.forEach(function (n) { if (t[n.id] && n.k === "active") s++; }); return s; }
  function rank(t, j, skill) {
    var r = 0;
    j.nodes.forEach(function (n) { if (t[n.id] && n.s === skill && (n.k === "active" || n.k === "passive" || n.k === "keystone" || n.k === "rank")) r++; });
    return r;
  }
  function stats(t, j) {
    var s = {}; D.statOrder.forEach(function (k) { s[k] = 0; });
    j.nodes.forEach(function (n) { if (t[n.id] && n.k === "stat") s[n.st] += n.am; });
    return s;
  }
  function canTake(t, j, n) {
    var m = byId(j);
    if (t[n.id]) return "TAKEN";
    if (n.r.length && !n.r.some(function (r) { return t[r]; })) return "REQUIRES";
    if (n.b.some(function (b) { return t[b]; })) return "EXCLUDED";
    if (n.k === "rune" && j.nodes.some(function (o) { return o !== n && o.k === "rune" && o.s === n.s && t[o.id]; })) return "EXCLUDED";
    if (n.a && n.ap > 0 && archPoints(t, j, n.a) < n.ap) return "ARCH_POINTS";
    if (n.k === "active" && actives(t, j) >= D.slots) return "ACTIVES_FULL";
    if (n.k === "rank" && j.skills[n.s] && rank(t, j, n.s) >= j.skills[n.s].m) return "RANK_MAX";
    if (n.k === "mutation" && j.skills[n.s] && rank(t, j, n.s) < j.skills[n.s].m) return "NOT_MASTERED";
    if (Math.min(state.lv, D.maxLevel) - spent(t, j) < n.c) return "NO_POINTS";
    void m;
    return "OK";
  }
  var WHY = {
    TAKEN: "取っています（押すと外れます）",
    REQUIRES: "前提のノードのどれかを先に取ります",
    EXCLUDED: "並んだノード（同じスキルの別の刻印など）を取っているので取れません",
    ARCH_POINTS: "その型のノードに、もっと点を使うと取れます",
    ACTIVES_FULL: "使うスキルはもう 4 つです",
    RANK_MAX: "このスキルの段はもう最大です",
    NOT_MASTERED: "先にそのスキルの段を上げきります",
    NO_POINTS: "点が足りません（レベルを上げると増えます）",
    OK: "押すと取れます"
  };

  // 外したあと: 幹からつながっているノードだけ残し、型の点・使うスキルの数・点の数が足りないノードも外す (くり返す)
  function settle(t, j) {
    var changed = true;
    while (changed) {
      changed = false;
      var seen = {}, grow = true;
      j.nodes.forEach(function (n) { if (t[n.id] && !n.r.length) seen[n.id] = true; });
      while (grow) {
        grow = false;
        j.nodes.forEach(function (n) {
          if (t[n.id] && !seen[n.id] && n.r.some(function (r) { return seen[r]; })) { seen[n.id] = true; grow = true; }
        });
      }
      j.nodes.forEach(function (n) {
        if (t[n.id] && !seen[n.id]) { delete t[n.id]; changed = true; }
      });
      j.nodes.forEach(function (n) {
        if (!t[n.id]) return;
        if ((n.a && n.ap > 0 && archPoints(t, j, n.a) - (n.a ? n.c : 0) < n.ap) ||
            (n.k === "mutation" && j.skills[n.s] && rank(t, j, n.s) < j.skills[n.s].m)) {
          delete t[n.id]; changed = true;
        }
      });
    }
    // 点がレベルを超えたら、いちばん下 (y の大きい) から外す
    var over = spent(t, j) - Math.min(state.lv, D.maxLevel);
    if (over > 0) {
      var list = j.nodes.filter(function (n) { return t[n.id]; }).sort(function (a, b) { return b.y - a.y || b.x - a.x; });
      for (var i = 0; i < list.length && spent(t, j) > Math.min(state.lv, D.maxLevel); i++) { delete t[list[i].id]; settle(t, j); }
    }
  }

  // ---------------------------------------------------------------- 描く
  function iconStyle(i, size) {
    var sheet = i >= 100 ? "icons2.png" : "icons.png", n = i >= 100 ? i - 100 : i;
    var k = size / 32;
    return "background-image:url(img/skill/" + sheet + ");background-size:" + (256 * k) + "px " + (192 * k) + "px;background-position:-" +
      ((n % 8) * 32 * k) + "px -" + (Math.floor(n / 8) * 32 * k) + "px";
  }
  function big(n) { return n.k === "keystone" || n.k === "trait"; }
  function draw() {
    var j = job(), t = state.taken, m = byId(j);
    var w = 0, h = 0;
    j.nodes.forEach(function (n) { w = Math.max(w, n.x); h = Math.max(h, n.y); });
    var W = (w + 1) * CELL, H = (h + 1) * CELL;
    var svg = '<svg class="pl-lines" width="' + W + '" height="' + H + '" viewBox="0 0 ' + W + " " + H + '" aria-hidden="true">';
    var drawn = {};
    j.nodes.forEach(function (n) {
      n.r.forEach(function (r) {
        var o = m[r]; if (!o) return;
        var key = n.id < r ? n.id + "|" + r : r + "|" + n.id;
        if (drawn[key]) return; drawn[key] = true;
        var on = t[n.id] && t[r];
        svg += '<line x1="' + (o.x * CELL + CELL / 2) + '" y1="' + (o.y * CELL + CELL / 2) + '" x2="' + (n.x * CELL + CELL / 2) + '" y2="' + (n.y * CELL + CELL / 2) + '" class="' + (on ? "on" : "") + '"/>';
      });
    });
    svg += "</svg>";
    var html = svg;
    j.nodes.forEach(function (n) {
      var why = canTake(t, j, n);
      var cls = "pl-node k-" + n.k + (t[n.id] ? " taken" : why === "OK" ? " open" : " shut") + (big(n) ? " big" : "");
      var size = big(n) ? 48 : ICON;
      var cap = n.k === "stat" ? "+" + n.am : n.k === "rank" ? "+1" : t[n.id] ? "" : n.c + "SP";
      html += '<button type="button" class="' + cls + '" data-id="' + n.id + '" style="left:' + (n.x * CELL + (CELL - size) / 2) + "px;top:" +
        (n.y * CELL + (CELL - size) / 2 - 4) + "px;width:" + size + "px;height:" + size + 'px" aria-label="' + esc(n.n) + '">' +
        '<span class="ic" style="' + iconStyle(n.i, size - 8) + '"></span>' + (cap ? '<span class="cap">' + cap + "</span>" : "") + "</button>";
    });
    board.style.width = W + "px";
    board.style.height = (H + 8) + "px";
    board.innerHTML = html;
    drawSum();
    writeHash();
  }
  function drawSum() {
    var j = job(), t = state.taken, used = spent(t, j), all = Math.min(state.lv, D.maxLevel), st = stats(t, j);
    var h = '<p class="pl-pts"><b>' + (all - used) + "</b> 点 残り <span>（" + used + " / " + all + " 使用）</span></p>";
    h += '<p class="pl-act">使うスキル <b>' + actives(t, j) + "</b> / " + D.slots + "</p>";
    h += '<div class="pl-archs">' + j.archs.map(function (a) {
      var p = archPoints(t, j, a.id);
      return '<div class="pl-arch"><span>' + esc(a.n) + "</span><b>" + p + '</b><i style="width:' + Math.min(100, p / 30 * 100) + '%"></i></div>';
    }).join("") + "</div>";
    h += '<dl class="pl-stats">' + D.statOrder.map(function (k) { return "<dt>" + D.stats[k] + "</dt><dd>" + st[k] + "</dd>"; }).join("") + "</dl>";
    var act = j.nodes.filter(function (n) { return t[n.id] && n.k === "active"; }).map(function (n) {
      return '<li><span class="ic" style="' + iconStyle(n.i, 24) + '"></span>' + esc(n.n) + " <small>段 " + rank(t, j, n.s) + "</small></li>";
    });
    h += '<ul class="pl-actives">' + (act.length ? act.join("") : '<li class="small">まだありません</li>') + "</ul>";
    sum.innerHTML = h;
  }
  function showInfo(n) {
    var j = job(), t = state.taken, m = byId(j), why = canTake(t, j, n);
    var h = '<p class="pl-kind">' + esc(D.kinds[n.k] || n.k) + (n.a ? " ・ " + esc((j.archs.filter(function (a) { return a.id === n.a; })[0] || {}).n || "") : " ・ 幹") +
      " ・ " + n.c + " 点</p><h3>" + esc(n.n) + "</h3><p>" + esc(n.e || "") + "</p>";
    if (n.k === "threshold" && n.st) {
      var have = stats(t, j)[n.st];
      h += '<p class="small">' + D.stats[n.st] + " が " + n.at + " 以上のとき効く（いま " + have + (have >= n.at ? "。効いています" : "") + "）</p>";
    }
    if (n.ap) h += '<p class="small">型の点 ' + n.ap + " で取れる（いま " + archPoints(t, j, n.a) + "）</p>";
    if (n.r.length) h += '<p class="small">前提（どれか一つ）: ' + n.r.map(function (r) { return esc((m[r] || {}).n || r); }).join("、") + "</p>";
    if (n.b.length) h += '<p class="small">並んで取れない: ' + n.b.map(function (r) { return esc((m[r] || {}).n || r); }).join("、") + "</p>";
    h += '<p class="pl-why ' + (why === "OK" || why === "TAKEN" ? "ok" : "no") + '">' + WHY[why] + "</p>";
    info.innerHTML = h;
  }

  // ---------------------------------------------------------------- 操作
  board.addEventListener("click", function (e) {
    var b = e.target.closest(".pl-node"); if (!b) return;
    var j = job(), n = byId(j)[b.dataset.id], t = state.taken;
    var why = canTake(t, j, n);
    if (why === "TAKEN") { delete t[n.id]; settle(t, j); }
    else if (why === "OK") { t[n.id] = true; }
    draw(); showInfo(n);
  });
  board.addEventListener("mouseover", function (e) {
    var b = e.target.closest(".pl-node"); if (!b) return;
    showInfo(byId(job())[b.dataset.id]);
  });
  board.addEventListener("focusin", function (e) {
    var b = e.target.closest(".pl-node"); if (!b) return;
    showInfo(byId(job())[b.dataset.id]);
  });
  lvIn.addEventListener("input", function () { state.lv = +lvIn.value; lvN.textContent = lvIn.value; settle(state.taken, job()); draw(); });
  document.getElementById("pl-reset").addEventListener("click", function () { state.taken = {}; draw(); });
  document.getElementById("pl-share").addEventListener("click", function () {
    writeHash();
    var b = this, url = location.href;
    (navigator.clipboard ? navigator.clipboard.writeText(url) : Promise.reject()).then(function () { b.textContent = "写しました"; }, function () { b.textContent = "アドレス欄から写せます"; });
    setTimeout(function () { b.textContent = "リンクを写す"; }, 1800);
  });
  var tabs = document.querySelector(".pl-jobs");
  tabs.innerHTML = D.jobs.map(function (j) { return '<button type="button" role="tab" data-job="' + j.id + '">' + esc(j.n) + "</button>"; }).join("");
  tabs.addEventListener("click", function (e) {
    var b = e.target.closest("button"); if (!b) return;
    state.job = b.dataset.job; state.taken = {}; markTabs(); draw();
    info.innerHTML = '<p class="small">ノードに指を置く（触れる）と、名前と効き目が出ます。</p>';
  });
  function markTabs() {
    Array.prototype.forEach.call(tabs.children, function (b) { b.setAttribute("aria-selected", b.dataset.job === state.job ? "true" : "false"); });
  }

  // 組んだ形をアドレスに: #job=warrior&lv=50&n=a,b,c
  function writeHash() {
    var ids = Object.keys(state.taken);
    var h = "#job=" + state.job + "&lv=" + state.lv + (ids.length ? "&n=" + ids.join(",") : "");
    if (location.hash !== h) history.replaceState(null, "", h);
  }
  function readHash() {
    var q = {};
    location.hash.replace(/^#/, "").split("&").forEach(function (kv) { var p = kv.split("="); if (p[0]) q[p[0]] = decodeURIComponent(p[1] || ""); });
    if (q.job && D.jobs.some(function (j) { return j.id === q.job; })) state.job = q.job;
    if (q.lv) state.lv = Math.max(1, Math.min(D.maxLevel, parseInt(q.lv, 10) || D.maxLevel));
    lvIn.value = state.lv; lvN.textContent = state.lv;
    state.taken = {};
    if (q.n) {
      // 決まりどおりに取れる順に足す (書き換えたリンクでも決まりを破らない)
      var j = job(), m = byId(j), want = q.n.split(","), grew = true;
      while (grew) {
        grew = false;
        want.forEach(function (id) { var n = m[id]; if (n && !state.taken[id] && canTake(state.taken, j, n) === "OK") { state.taken[id] = true; grew = true; } });
      }
    }
  }
  readHash();
  markTabs();
  draw();
  // 狭い画面では、盤を幹 (真ん中) に合わせて開く
  var wrap = board.parentNode;
  if (wrap.scrollWidth > wrap.clientWidth) wrap.scrollLeft = (wrap.scrollWidth - wrap.clientWidth) / 2;
})();
