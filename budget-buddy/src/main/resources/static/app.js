import { prepareTransactionAttempt } from "./transaction-request.mjs";

const state = {
    csrf: null,
    user: null,
    dashboard: null,
    transactions: [],
    categories: [],
    csvFile: null,
    csvPreview: null,
    csvMapping: null,
    csvResult: null,
    transactionAttempt: null
};

const viewMeta = {
    dashboard: ["OVERVIEW", "대시보드"],
    transactions: ["MONEY LOG", "거래 내역"],
    ai: ["SMART INSIGHT", "AI 리포트"],
    csv: ["FILE ANALYSIS", "CSV 분석"]
};

const categoryColors = ["#2f7464", "#e8a94a", "#557c96", "#b76b63", "#7b8f66", "#856f9c", "#ba8b59", "#4f8d89"];

class ApiError extends Error {
    constructor(message, status, code) {
        super(message);
        this.status = status;
        this.code = code;
    }
}

async function api(path, options = {}) {
    const request = { credentials: "same-origin", ...options };
    request.headers = new Headers(options.headers || {});
    const method = (request.method || "GET").toUpperCase();
    if (!["GET", "HEAD", "OPTIONS"].includes(method) && state.csrf) {
        request.headers.set(state.csrf.headerName, state.csrf.token);
    }
    if (request.body && !(request.body instanceof FormData) && typeof request.body !== "string") {
        request.headers.set("Content-Type", "application/json");
        request.body = JSON.stringify(request.body);
    }

    const response = await fetch(path, request);
    const contentType = response.headers.get("content-type") || "";
    const data = response.status === 204
        ? null
        : contentType.includes("application/json")
            ? await response.json()
            : await response.text();

    if (!response.ok) {
        const message = data?.message || (response.status === 401 ? "로그인이 필요합니다." : "요청을 처리하지 못했습니다.");
        throw new ApiError(message, response.status, data?.code);
    }
    return data;
}

document.addEventListener("DOMContentLoaded", initialize);

async function initialize() {
    bindEvents();
    document.querySelector("#today-label").textContent = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric", month: "long", day: "numeric", weekday: "short"
    }).format(new Date());
    try {
        state.csrf = await api("/api/auth/csrf");
        state.user = await api("/api/auth/me");
        await enterApplication();
    } catch (error) {
        const authenticationRequired = error instanceof ApiError && [401, 403].includes(error.status);
        showLogin(authenticationRequired ? "" : startupError(error));
    }
}

function bindEvents() {
    document.querySelector("#login-form").addEventListener("submit", handleLogin);
    document.querySelector("#logout-button").addEventListener("click", handleLogout);
    document.querySelectorAll(".nav-item, .link-view, [data-view-link]").forEach(control => {
        control.addEventListener("click", event => {
            event.preventDefault();
            navigate(control.dataset.view || control.dataset.viewLink);
        });
    });
    document.querySelector("#menu-button").addEventListener("click", () => {
        setSidebarOpen(!document.querySelector(".sidebar").classList.contains("open"));
    });
    window.addEventListener("hashchange", navigateFromHash);
    document.addEventListener("keydown", event => {
        if (event.key === "Escape") setSidebarOpen(false);
    });
    document.querySelector("#quick-add-button").addEventListener("click", openTransactionDialog);
    document.querySelectorAll("[data-close-dialog]").forEach(button => button.addEventListener("click", closeTransactionDialog));
    document.querySelector("#transaction-form").addEventListener("submit", createTransaction);
    document.querySelector("#new-type").addEventListener("change", renderNewCategoryOptions);
    ["#transaction-search", "#filter-start", "#filter-end", "#filter-category"].forEach(selector => {
        document.querySelector(selector).addEventListener("input", renderTransactions);
    });
    document.querySelector("#filter-reset").addEventListener("click", resetFilters);
    document.querySelector("#generate-ai-button").addEventListener("click", generateMonthlyReport);
    document.querySelector("#generate-demo-button").addEventListener("click", generateDemoData);
    document.querySelector("#csv-file").addEventListener("change", event => selectCsvFile(event.target.files[0]));
    document.querySelector("#analyze-csv-button").addEventListener("click", analyzeCsv);
    document.querySelector("#csv-ai-button").addEventListener("click", generateCsvReport);

    const dropZone = document.querySelector("#drop-zone");
    ["dragenter", "dragover"].forEach(type => dropZone.addEventListener(type, event => {
        event.preventDefault();
        dropZone.classList.add("dragover");
    }));
    ["dragleave", "drop"].forEach(type => dropZone.addEventListener(type, event => {
        event.preventDefault();
        dropZone.classList.remove("dragover");
    }));
    dropZone.addEventListener("drop", event => selectCsvFile(event.dataTransfer.files[0]));
}

async function handleLogin(event) {
    event.preventDefault();
    const errorElement = document.querySelector("#login-error");
    const button = event.currentTarget.querySelector("button[type=submit]");
    errorElement.textContent = "";
    setLoading(button, true, "로그인 중…");
    try {
        state.user = await api("/api/auth/login", {
            method: "POST",
            body: {
                email: document.querySelector("#login-email").value.trim(),
                password: document.querySelector("#login-password").value
            }
        });
        await enterApplication();
    } catch (error) {
        errorElement.textContent = friendlyError(error);
    } finally {
        setLoading(button, false);
    }
}

async function handleLogout() {
    try {
        await api("/api/auth/logout", { method: "POST" });
    } finally {
        window.location.reload();
    }
}

async function enterApplication() {
    document.querySelector("#login-screen").classList.add("hidden");
    document.querySelector("#app").classList.remove("hidden");
    document.querySelector("#user-email").textContent = state.user.email;
    document.querySelector("#user-name").textContent = state.user.email.split("@")[0];
    document.querySelector(".avatar").textContent = state.user.email.charAt(0).toUpperCase();
    navigateFromHash();
    await Promise.all([loadDashboard(), loadTransactionData(), loadReports()]);
}

function showLogin(message = "") {
    document.querySelector("#login-screen").classList.remove("hidden");
    document.querySelector("#app").classList.add("hidden");
    document.querySelector("#login-error").textContent = message;
}

function navigateFromHash() {
    if (!state.user) return;
    const requestedView = location.hash.replace(/^#/, "");
    const view = viewMeta[requestedView] ? requestedView : "dashboard";
    if (requestedView !== view) history.replaceState(null, "", `#${view}`);
    navigate(view, false);
}

function navigate(view, updateHash = true) {
    if (!viewMeta[view]) return;
    document.querySelectorAll(".view").forEach(section => section.classList.toggle("active-view", section.id === `view-${view}`));
    document.querySelectorAll(".nav-item").forEach(button => {
        const active = button.dataset.view === view;
        button.classList.toggle("active", active);
        if (active) button.setAttribute("aria-current", "page");
        else button.removeAttribute("aria-current");
    });
    document.querySelector("#page-kicker").textContent = viewMeta[view][0];
    document.querySelector("#page-title").textContent = viewMeta[view][1];
    setSidebarOpen(false);
    if (updateHash && location.hash !== `#${view}`) location.hash = view;
}

function setSidebarOpen(open) {
    const sidebar = document.querySelector(".sidebar");
    const menuButton = document.querySelector("#menu-button");
    sidebar.classList.toggle("open", open);
    menuButton.setAttribute("aria-expanded", String(open));
    menuButton.setAttribute("aria-label", open ? "메뉴 닫기" : "메뉴 열기");
}

async function loadDashboard() {
    document.querySelector("#dashboard-loading").classList.remove("hidden");
    document.querySelector("#dashboard-content").classList.add("hidden");
    try {
        state.dashboard = await api("/api/analysis/dashboard");
        renderDashboard(state.dashboard);
    } catch (error) {
        document.querySelector("#dashboard-loading").textContent = friendlyError(error);
    }
}

function renderDashboard(data) {
    document.querySelector("#metric-balance").textContent = won(data.currentBalance);
    document.querySelector("#metric-income").textContent = won(data.totalIncome);
    document.querySelector("#metric-expense").textContent = won(data.totalExpense);
    document.querySelector("#metric-category").textContent = data.topCategory || "아직 없음";
    document.querySelector("#income-caption").textContent = `${data.incomeExpenseComparison.at(-1)?.income > 0 ? "이번 달 수입이 반영됐어요" : "이번 달 수입 거래가 없어요"}`;
    document.querySelector("#expense-change").textContent = data.expenseChangeRate == null
        ? "비교할 이전 기간 데이터 없음"
        : `전월 동기간보다 ${Math.abs(Number(data.expenseChangeRate)).toFixed(1)}% ${Number(data.expenseChangeRate) >= 0 ? "증가" : "감소"}`;
    document.querySelector("#category-caption").textContent = data.categoryExpenses[0]
        ? `${won(data.categoryExpenses[0].amount)} · ${data.categoryExpenses[0].count}건`
        : "아직 지출이 없어요";
    document.querySelector("#highlight-vendor").textContent = data.topVendor || "-";
    document.querySelector("#highlight-average").textContent = won(data.averageExpense);
    document.querySelector("#highlight-largest").textContent = data.largestExpense
        ? `${data.largestExpense.description} · ${won(data.largestExpense.amount)}`
        : "-";
    document.querySelector("#highlight-count").textContent = `${data.transactionCount}건`;

    renderLineChart("monthly-chart", data.monthlyExpenses, "최근 6개월 월별 지출 추이");
    renderLineChart("weekly-chart", data.weeklyExpenses, "최근 8주 주간 지출 추이");
    renderDonutChart("category-chart", data.categoryExpenses);
    renderCashflowChart("cashflow-chart", data.incomeExpenseComparison);
    renderRecentTransactions();
    document.querySelector("#dashboard-loading").classList.add("hidden");
    document.querySelector("#dashboard-content").classList.remove("hidden");
}

async function generateDemoData() {
    const button = document.querySelector("#generate-demo-button");
    setLoading(button, true, "샘플 생성 중…");
    try {
        await api("/api/demo/seed?count=30", { method: "POST" });
        showToast("샘플 거래 30건을 추가했습니다.");
        await Promise.all([loadTransactionData(), loadDashboard(), loadReports()]);
    } catch (error) {
        showToast(friendlyError(error), true);
    } finally {
        setLoading(button, false);
    }
}

async function loadTransactionData() {
    try {
        [state.transactions, state.categories] = await Promise.all([
            api("/api/transactions"),
            api("/api/categories")
        ]);
        renderCategoryFilters();
        renderTransactions();
        renderRecentTransactions();
        document.querySelector("#demo-banner").classList.toggle("hidden", state.transactions.length > 0);
    } catch (error) {
        showToast(friendlyError(error), true);
    }
}

function renderCategoryFilters() {
    const filter = document.querySelector("#filter-category");
    filter.innerHTML = `<option value="">전체 카테고리</option>${state.categories.map(category =>
        `<option value="${category.categoryId}">${escapeHtml(category.name)}</option>`).join("")}`;
    renderNewCategoryOptions();
}

function renderNewCategoryOptions() {
    const type = document.querySelector("#new-type").value;
    const categories = state.categories.filter(category => category.type === type);
    document.querySelector("#new-category").innerHTML = categories.length
        ? categories.map(category => `<option value="${category.categoryId}">${escapeHtml(category.name)}</option>`).join("")
        : `<option value="">사용 가능한 카테고리 없음</option>`;
}

function filteredTransactions() {
    const search = document.querySelector("#transaction-search").value.trim().toLowerCase();
    const start = document.querySelector("#filter-start").value;
    const end = document.querySelector("#filter-end").value;
    const category = document.querySelector("#filter-category").value;
    return state.transactions.filter(transaction => {
        const text = `${transaction.vendorName || ""} ${transaction.description || ""}`.toLowerCase();
        const date = transaction.transactionAt.slice(0, 10);
        return (!search || text.includes(search))
            && (!start || date >= start)
            && (!end || date <= end)
            && (!category || String(transaction.categoryId) === category);
    });
}

function renderTransactions() {
    const transactions = filteredTransactions();
    document.querySelector("#transaction-count").textContent = `${transactions.length}건`;
    const tbody = document.querySelector("#transaction-list");
    if (!transactions.length) {
        tbody.innerHTML = emptyRow(6, "조건에 맞는 거래가 없습니다.");
        return;
    }
    tbody.innerHTML = transactions.map(transaction => {
        const category = categoryName(transaction.categoryId);
        const vendor = transaction.vendorName || transaction.description || "사용처 미입력";
        const memo = transaction.vendorName && transaction.description ? `<small>${escapeHtml(transaction.description)}</small>` : "";
        return `<tr>
            <td>${formatDate(transaction.transactionAt)}</td>
            <td><strong>${escapeHtml(vendor)}</strong>${memo}</td>
            <td>${escapeHtml(category)}</td>
            <td><span class="status ${transaction.transactionType.toLowerCase()}">${typeLabel(transaction.transactionType)}</span></td>
            <td>${won(transaction.balanceAfter)}</td>
            <td class="align-right ${transaction.transactionType === "INCOME" ? "amount-income" : "amount-expense"}">${transaction.transactionType === "INCOME" ? "+" : "-"}${won(transaction.amount)}</td>
        </tr>`;
    }).join("");
}

function renderRecentTransactions() {
    const tbody = document.querySelector("#recent-transactions");
    if (!tbody) return;
    const recent = state.transactions.slice(0, 6);
    if (!recent.length) {
        tbody.innerHTML = emptyRow(5, "첫 거래를 추가하면 여기에 표시됩니다.");
        return;
    }
    tbody.innerHTML = recent.map(transaction => `<tr>
        <td>${formatDate(transaction.transactionAt)}</td>
        <td>${escapeHtml(transaction.vendorName || transaction.description || "사용처 미입력")}</td>
        <td>${escapeHtml(categoryName(transaction.categoryId))}</td>
        <td><span class="status ${transaction.transactionType.toLowerCase()}">${typeLabel(transaction.transactionType)}</span></td>
        <td class="align-right ${transaction.transactionType === "INCOME" ? "amount-income" : "amount-expense"}">${transaction.transactionType === "INCOME" ? "+" : "-"}${won(transaction.amount)}</td>
    </tr>`).join("");
}

function resetFilters() {
    ["#transaction-search", "#filter-start", "#filter-end", "#filter-category"].forEach(selector => document.querySelector(selector).value = "");
    renderTransactions();
}

function openTransactionDialog() {
    document.querySelector("#transaction-error").textContent = "";
    document.querySelector("#new-date").value = localDateTimeValue(new Date());
    renderNewCategoryOptions();
    document.querySelector("#transaction-dialog").showModal();
}

function closeTransactionDialog() {
    document.querySelector("#transaction-dialog").close();
}

async function createTransaction(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = form.querySelector("button[type=submit]");
    const errorElement = document.querySelector("#transaction-error");
    errorElement.textContent = "";
    setLoading(submit, true, "저장 중…");
    try {
        state.transactionAttempt = prepareTransactionAttempt({
                categoryId: Number(document.querySelector("#new-category").value),
                amount: document.querySelector("#new-amount").value,
                transactionType: document.querySelector("#new-type").value,
                description: document.querySelector("#new-description").value.trim() || null,
                vendorName: document.querySelector("#new-vendor").value.trim() || null,
                location: null,
                transactionAt: document.querySelector("#new-date").value
            }, state.transactionAttempt);
        await api("/api/transactions", {
            method: "POST",
            body: state.transactionAttempt.body
        });
        state.transactionAttempt = null;
        closeTransactionDialog();
        form.reset();
        showToast("거래가 안전하게 저장되었습니다.");
        await Promise.all([loadTransactionData(), loadDashboard()]);
    } catch (error) {
        errorElement.textContent = friendlyError(error);
    } finally {
        setLoading(submit, false);
    }
}

async function loadReports() {
    try {
        const reports = await api("/api/ai/reports");
        renderReports(reports);
    } catch (error) {
        document.querySelector("#ai-report-list").innerHTML = `<div class="empty-state">${escapeHtml(friendlyError(error))}</div>`;
    }
}

function renderReports(reports) {
    const container = document.querySelector("#ai-report-list");
    if (!reports.length) {
        container.innerHTML = `<div class="empty-state">아직 생성된 리포트가 없습니다.<br>이번 달 소비 리포트를 만들어 보세요.</div>`;
        return;
    }
    container.innerHTML = reports.map(report => `<article class="card report-card">
        <div class="report-meta"><span class="status income">${report.reportType === "CSV_ANALYSIS" ? "CSV 임시 분석" : "월간 분석"}</span><time>${formatDateTime(report.generatedAt)}</time></div>
        <div class="report-content">${renderReportText(report.reportContent)}</div>
    </article>`).join("");
}

async function generateMonthlyReport() {
    const button = document.querySelector("#generate-ai-button");
    setLoading(button, true, "Gemini가 해석 중…");
    try {
        await api("/api/ai/report/monthly", { method: "POST" });
        await loadReports();
        showToast("새 AI 리포트를 저장했습니다.");
    } catch (error) {
        showToast(friendlyError(error), true);
    } finally {
        setLoading(button, false);
    }
}

async function selectCsvFile(file) {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".csv")) {
        showToast("CSV 파일을 선택해 주세요.", true);
        return;
    }
    state.csvFile = file;
    state.csvResult = null;
    const selected = document.querySelector("#selected-file");
    selected.innerHTML = `<div><strong>${escapeHtml(file.name)}</strong><br><span class="muted">${formatBytes(file.size)}</span></div><button class="text-button" id="change-file">다른 파일</button>`;
    selected.classList.remove("hidden");
    document.querySelector("#drop-zone").classList.add("hidden");
    document.querySelector("#change-file").addEventListener("click", resetCsv);
    setCsvStep(1);

    try {
        const formData = new FormData();
        formData.append("file", file);
        state.csvPreview = await api("/api/csv/preview", { method: "POST", body: formData });
        renderCsvPreview();
        renderMapping();
        setCsvStep(3);
    } catch (error) {
        showToast(friendlyError(error), true);
        resetCsv();
    }
}

function resetCsv() {
    state.csvFile = null;
    state.csvPreview = null;
    state.csvMapping = null;
    state.csvResult = null;
    document.querySelector("#csv-file").value = "";
    document.querySelector("#drop-zone").classList.remove("hidden");
    document.querySelector("#selected-file").classList.add("hidden");
    ["#preview-card", "#mapping-card", "#validation-card", "#csv-result"].forEach(selector => document.querySelector(selector).classList.add("hidden"));
    setCsvStep(1);
}

function renderCsvPreview() {
    const preview = state.csvPreview;
    document.querySelector("#csv-row-count").textContent = `전체 ${preview.totalRows}행`;
    document.querySelector("#preview-head").innerHTML = `<tr>${preview.headers.map(header => `<th>${escapeHtml(header)}</th>`).join("")}</tr>`;
    document.querySelector("#preview-body").innerHTML = preview.rows.map(row => `<tr>${preview.headers.map(header => `<td>${escapeHtml(row[header] || "")}</td>`).join("")}</tr>`).join("");
    document.querySelector("#preview-card").classList.remove("hidden");
}

function renderMapping() {
    const fields = [
        ["date", "거래 날짜", true],
        ["description", "사용처 / 설명", false],
        ["category", "카테고리", true],
        ["amount", "금액", true],
        ["type", "수입 / 지출", true]
    ];
    document.querySelector("#mapping-grid").innerHTML = fields.map(([field, label, required]) => `<div class="mapping-row">
        <label for="mapping-${field}" class="${required ? "required" : ""}">${label}</label><span aria-hidden="true">←</span>
        <select id="mapping-${field}" data-mapping="${field}">
            <option value="">${required ? "컬럼 선택" : "연결하지 않음"}</option>
            ${state.csvPreview.headers.map(header => `<option value="${escapeAttribute(header)}" ${state.csvPreview.suggestedMapping[field] === header ? "selected" : ""}>${escapeHtml(header)}</option>`).join("")}
        </select>
    </div>`).join("");
    document.querySelector("#mapping-card").classList.remove("hidden");
}

function currentCsvMapping() {
    return Object.fromEntries([...document.querySelectorAll("[data-mapping]")].map(select => [select.dataset.mapping, select.value || null]));
}

async function analyzeCsv() {
    if (!state.csvFile) return;
    const button = document.querySelector("#analyze-csv-button");
    state.csvMapping = currentCsvMapping();
    setLoading(button, true, "검증 중…");
    try {
        const formData = csvFormData();
        state.csvResult = await api("/api/csv/analyze", { method: "POST", body: formData });
        renderValidation(state.csvResult.validation);
        if (state.csvResult.analysis) {
            renderCsvAnalysis(state.csvResult.analysis);
            setCsvStep(5);
        } else {
            document.querySelector("#csv-result").classList.add("hidden");
            setCsvStep(4);
        }
    } catch (error) {
        showToast(friendlyError(error), true);
    } finally {
        setLoading(button, false);
    }
}

function csvFormData() {
    const formData = new FormData();
    formData.append("file", state.csvFile);
    formData.append("mapping", new Blob([JSON.stringify(state.csvMapping)], { type: "application/json" }));
    return formData;
}

function renderValidation(validation) {
    document.querySelector("#validation-summary").innerHTML = [
        ["전체 데이터", validation.totalRows, ""],
        ["정상 데이터", validation.validRows, "ok"],
        ["오류 데이터", validation.errorRows, validation.errorRows ? "bad" : "ok"],
        ["중복 의심", validation.suspectedDuplicates, validation.suspectedDuplicates ? "bad" : ""]
    ].map(([label, value, type]) => `<div class="validation-item ${type}"><span>${label}</span><strong>${value}건</strong></div>`).join("");
    const errors = document.querySelector("#csv-errors");
    errors.innerHTML = validation.errors.length
        ? validation.errors.map(error => `<div class="error-item"><strong>${error.rowNumber}행</strong><span>${escapeHtml(error.field)}</span><span>${escapeHtml(error.message)}</span></div>`).join("")
        : `<p class="muted">모든 행이 올바르게 인식되었습니다.</p>`;
    document.querySelector("#validation-card").classList.remove("hidden");
}

function renderCsvAnalysis(data) {
    const metrics = [
        ["분석 기간 수입", won(data.totalIncome), `${data.periodStart} ~ ${data.periodEnd}`],
        ["분석 기간 지출", won(data.totalExpense), `${data.transactionCount}건의 유효 거래`],
        ["순 현금 흐름", won(data.currentBalance), "수입 - 지출"],
        ["가장 큰 소비 영역", data.topCategory || "-", data.categoryExpenses[0] ? won(data.categoryExpenses[0].amount) : "지출 없음"]
    ];
    document.querySelector("#csv-metrics").innerHTML = metrics.map(([label, value, caption], index) => `<article class="metric-card ${index === 2 ? "accent-card" : ""}"><div class="metric-heading"><span>${label}</span></div><strong class="${index === 3 ? "text-metric" : ""}">${escapeHtml(value)}</strong><small>${escapeHtml(caption)}</small></article>`).join("");
    renderLineChart("csv-monthly-chart", data.monthlyExpenses, "CSV 데이터의 월별 지출 추이");
    renderDonutChart("csv-category-chart", data.categoryExpenses);
    document.querySelector("#csv-ai-report").classList.add("hidden");
    document.querySelector("#csv-result").classList.remove("hidden");
}

async function generateCsvReport() {
    if (!state.csvResult?.analysis) return;
    const button = document.querySelector("#csv-ai-button");
    setLoading(button, true, "Gemini가 해석 중…");
    try {
        const report = await api("/api/csv/ai-report", { method: "POST", body: csvFormData() });
        const card = document.querySelector("#csv-ai-report");
        card.innerHTML = `<div class="report-meta"><span class="status income">CSV AI 분석</span><span>방금 생성됨</span></div><div class="report-content">${renderReportText(report)}</div>`;
        card.classList.remove("hidden");
        await loadReports();
    } catch (error) {
        showToast(friendlyError(error), true);
    } finally {
        setLoading(button, false);
    }
}

function setCsvStep(step) {
    document.querySelectorAll(".steps li").forEach(item => {
        const itemStep = Number(item.dataset.step);
        item.classList.toggle("active", itemStep <= step);
        if (itemStep === step) item.setAttribute("aria-current", "step");
        else item.removeAttribute("aria-current");
    });
}

function renderLineChart(containerId, points, chartLabel = "지출 추이") {
    const container = document.getElementById(containerId);
    if (!points?.length || points.every(point => Number(point.amount) === 0)) {
        container.innerHTML = `<div class="empty-state">표시할 지출 데이터가 없습니다.</div>`;
        return;
    }
    const width = 680;
    const height = 220;
    const padding = { top: 22, right: 20, bottom: 28, left: 22 };
    const max = Math.max(...points.map(point => Number(point.amount)), 1);
    const stepX = (width - padding.left - padding.right) / Math.max(points.length - 1, 1);
    const coords = points.map((point, index) => ({
        x: padding.left + index * stepX,
        y: padding.top + (height - padding.top - padding.bottom) * (1 - Number(point.amount) / max),
        ...point
    }));
    const polyline = coords.map(point => `${point.x},${point.y}`).join(" ");
    const area = `${padding.left},${height - padding.bottom} ${polyline} ${coords.at(-1).x},${height - padding.bottom}`;
    const gradientId = `area-gradient-${containerId}`;
    const grid = [0, .25, .5, .75, 1].map(value => {
        const y = padding.top + (height - padding.top - padding.bottom) * value;
        return `<line x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}" class="chart-grid-line"/>`;
    }).join("");
    const description = points.map(point => `${point.label} ${won(point.amount)}`).join(", ");
    container.innerHTML = `<svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img" aria-labelledby="${containerId}-title ${containerId}-description">
        <title id="${containerId}-title">${escapeHtml(chartLabel)}</title>
        <desc id="${containerId}-description">${escapeHtml(description)}</desc>
        <defs><linearGradient id="${gradientId}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2f7464"/><stop offset="1" stop-color="#fff"/></linearGradient></defs>
        ${grid}<polygon points="${area}" fill="url(#${gradientId})" opacity=".2"/><polyline points="${polyline}" class="chart-line"/>
        ${coords.map(point => `<circle cx="${point.x}" cy="${point.y}" r="4" class="chart-point"/><text x="${point.x}" y="${height - 7}" text-anchor="middle" class="chart-label">${escapeHtml(point.label)}</text><text x="${point.x}" y="${Math.max(point.y - 10, 10)}" text-anchor="middle" class="chart-value">${shortWon(point.amount)}</text>`).join("")}
    </svg>`;
}

function renderDonutChart(containerId, categories) {
    const container = document.getElementById(containerId);
    const allCategories = (categories || []).filter(item => Number(item.amount) > 0);
    const total = allCategories.reduce((sum, item) => sum + Number(item.amount), 0);
    if (!total) {
        container.innerHTML = `<div class="empty-state">카테고리 지출이 없습니다.</div>`;
        return;
    }
    const topCategories = allCategories.slice(0, 6);
    const remainingCategories = allCategories.slice(6);
    const visible = remainingCategories.length
        ? [...topCategories, {
            category: "기타",
            amount: remainingCategories.reduce((sum, item) => sum + Number(item.amount), 0),
            count: remainingCategories.reduce((sum, item) => sum + Number(item.count || 0), 0)
        }]
        : topCategories;
    let current = 0;
    const segments = visible.map((item, index) => {
        const start = current;
        current += Number(item.amount) / total * 100;
        return `${categoryColors[index]} ${start}% ${current}%`;
    });
    const description = visible.map(item => `${item.category} ${won(item.amount)}`).join(", ");
    container.innerHTML = `<div class="donut" role="img" aria-label="카테고리별 지출: ${escapeAttribute(description)}" style="background:conic-gradient(${segments.join(",")})"><div class="donut-center"><strong>${allCategories.length}개</strong><small>카테고리</small></div></div>
        <div class="chart-legend">${visible.map((item, index) => `<div><span class="color-dot" style="background:${categoryColors[index]}"></span><span>${escapeHtml(item.category)}</span><strong>${Math.round(Number(item.amount) / total * 100)}%</strong></div>`).join("")}</div>`;
}

function renderCashflowChart(containerId, points) {
    const container = document.getElementById(containerId);
    const max = Math.max(...points.flatMap(point => [Number(point.income), Number(point.expense)]), 1);
    container.innerHTML = points.map(point => `<div class="bar-row" role="img" aria-label="${escapeAttribute(point.label)} 수입 ${escapeAttribute(won(point.income))}, 지출 ${escapeAttribute(won(point.expense))}"><span aria-hidden="true">${escapeHtml(point.label)}</span><div class="bar-pair" aria-hidden="true"><div class="bar-track" title="수입 ${won(point.income)}"><div class="bar-fill income" style="width:${Number(point.income) / max * 100}%"></div></div><div class="bar-track" title="지출 ${won(point.expense)}"><div class="bar-fill expense" style="width:${Number(point.expense) / max * 100}%"></div></div></div></div>`).join("");
}

function renderReportText(text) {
    return escapeHtml(text || "")
        .replace(/^### (.+)$/gm, "<h3>$1</h3>")
        .replace(/^[-*] (.+)$/gm, "• $1")
        .replace(/\n/g, "<br>");
}

function categoryName(categoryId) {
    return state.categories.find(category => category.categoryId === categoryId)?.name || "미분류";
}

function typeLabel(type) { return type === "INCOME" ? "수입" : "지출"; }

function won(value) {
    return `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 }).format(Number(value || 0))}원`;
}

function shortWon(value) {
    const number = Number(value || 0);
    if (number >= 100000000) return `${(number / 100000000).toFixed(1)}억`;
    if (number >= 10000) return `${Math.round(number / 10000)}만`;
    return new Intl.NumberFormat("ko-KR").format(number);
}

function formatDate(value) {
    if (!value) return "-";
    const [year, month, day] = value.slice(0, 10).split("-");
    return `${year}.${month}.${day}`;
}

function formatDateTime(value) {
    if (!value) return "생성 시각 기록 중";
    return value.replace("T", " ").slice(0, 16);
}

function localDateTimeValue(date) {
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function emptyRow(columns, message) { return `<tr class="empty-row"><td colspan="${columns}">${escapeHtml(message)}</td></tr>`; }

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>'"]/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[character]);
}

function escapeAttribute(value) { return escapeHtml(value); }

function formatBytes(bytes) {
    return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function setLoading(button, loading, text) {
    if (loading) {
        button.dataset.originalText = button.textContent;
        button.textContent = text;
        button.disabled = true;
    } else {
        button.textContent = button.dataset.originalText || button.textContent;
        button.disabled = false;
    }
}

function showToast(message, error = false) {
    const toast = document.querySelector("#toast");
    toast.textContent = message;
    toast.classList.toggle("error", error);
    toast.classList.add("show");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 3200);
}

function friendlyError(error) {
    const messages = {
        C001: "입력값의 형식과 필수 항목을 확인해 주세요.",
        C002: "지원하지 않는 요청 방식입니다.",
        C005: "요청한 기능을 찾을 수 없습니다.",
        C006: "다른 거래를 처리 중입니다. 잠시 후 같은 내용으로 다시 저장해 주세요.",
        C007: "기존 데이터와 충돌해 저장하지 못했습니다. 입력 내용을 확인해 주세요.",
        T002: "현재 잔액보다 큰 지출은 등록할 수 없습니다.",
        T003: "거래 구분과 카테고리 구분이 맞지 않습니다.",
        T004: "같은 요청 키가 다른 거래에 이미 사용되었습니다. 화면을 새로고침해 주세요.",
        T005: "등록 후 잔액이 서비스에서 지원하는 금액 범위를 초과합니다.",
        A001: "분석할 거래 데이터가 없습니다.",
        A002: "Gemini API 키가 설정되지 않았습니다. 실행 환경변수를 확인해 주세요.",
        A003: "AI 서비스에 잠시 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        AUTH001: "이메일 또는 비밀번호를 확인해 주세요.",
        AUTH002: "로그인이 필요합니다.",
        AUTH003: "이 작업을 수행할 권한이 없습니다.",
        D001: "샘플 데이터는 거래가 없는 계정에 한 번만 추가할 수 있습니다."
    };
    return messages[error?.code] || error?.message || "요청을 처리하지 못했습니다.";
}

function startupError(error) {
    if (error instanceof TypeError) {
        return "서버에 연결할 수 없습니다. Spring Boot가 실행 중인지 확인한 뒤 새로고침해 주세요.";
    }
    return `화면을 준비하지 못했습니다. ${friendlyError(error)} 새로고침 후 다시 시도해 주세요.`;
}
