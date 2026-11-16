const decimalFormat = new Intl.NumberFormat("es-MX", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const artwork = {
    "Espresso": ["coffee", "#905f43"],
    "Americano": ["coffee-dark", "#754c39"],
    "Latte": ["latte", "#a77a57"],
    "Café helado": ["iced", "#a16c46"],
    "Té verde": ["tea", "#77945d"],
    "Jugo de naranja": ["juice", "#df9b32"],
    "Pan artesanal": ["bread", "#a97745"],
    "Croissant": ["croissant", "#c58d42"],
    "Pastel del día": ["cake", "#ba777c"]
};
const cart = new Map();
let products = [];
let activeCategory = "Todos";
let paymentMethod = "Efectivo";
let pagosParciales = [];
let montoRestante = 0;
let totalCheckout = 0;
let ventaExenta = false;
let ivaRate = 0.13;
let currencySymbol = "$";
let systemSettings = null;
let currentProfile = null;
let currentShift = null;
let toastTimer;
let manualDiscountAmount = 0;
let discountAuthorizationToken = null;
let pendingDiscountTarget = "ticket";
let pendingDiscountProductId = null;
let automaticDiscountAmount = 0;
let automaticPromotionName = null;
let promotionQuoteSequence = 0;
let promotionQuoteTimer;
let isAdmin = false;
let canProcessReturns = false;
let adminProducts = [];
let editingProduct = null;
let editingCustomer = null;
let clientes = [];
let checkoutClientes = [];
let selectedCheckoutCustomer = null;
let receiptType = "CONSUMIDOR_FINAL";
let customerSearchSequence = 0;
let checkoutCustomerSearchSequence = 0;
let customerSearchTimer;
let activitySearchSequence = 0;
let activitySearchTimer;
let actividadesEconomicas = [];
let proveedores = [];
let editingSupplier = null;
let supplierQuickCreate = false;
let purchaseProducts = [];
let purchaseHistory = [];
let purchaseQuickCreate = false;
let activePurchaseLine = null;
let salesPage = 0;
let salesPageCount = 0;
let currentSale = null;
let pendingReturnItems = [];
let pendingExchangeFolio = null;
let reportLoaded = false;
const themeModes = new Set(["light", "dark"]);
const themeFamilies = new Set(["theme-slate", "theme-midnight", "theme-zinc", "theme-corporate"]);

function aplicarPreferenciasVisuales() {
    const theme = localStorage.getItem("vento.theme");
    const family = localStorage.getItem("vento.themeFamily");
    document.documentElement.dataset.theme = themeModes.has(theme) ? theme : "light";
    document.documentElement.dataset.themeFamily = themeFamilies.has(family) ? family : "theme-slate";
    document.querySelectorAll("[data-theme-mode]").forEach(button => {
        button.setAttribute("aria-pressed", String(button.dataset.themeMode === document.documentElement.dataset.theme));
    });
    document.querySelectorAll("[data-theme-family]").forEach(button => {
        button.setAttribute("aria-pressed", String(button.dataset.themeFamily === document.documentElement.dataset.themeFamily));
    });
}

function setTicketText(id, value) {
    const element = document.querySelector(id);
    if (element) element.textContent = value == null || value === "" ? "-" : String(value);
}

function dteValue(dte, ...keys) {
    for (const key of keys) {
        if (dte?.[key]) return dte[key];
    }
    return null;
}

function populateThermalTicket(saleResponse, dte = null) {
    const sale = saleResponse;
    const details = sale.detalles ?? [];
    const total = Number(sale.total ?? 0);
    const contingency = sale.enContingencia === true
        || sale.contingencia === true
        || sale.estado === "CONTINGENCIA"
        || sale.estadoDte === "CONTINGENCIA";
    const code = dteValue(dte, "codigoGeneracion") ?? dteValue(sale, "codigoGeneracion");
    const control = dteValue(dte, "numeroControl") ?? dteValue(sale, "numeroControl");
    const sello = dteValue(dte, "selloRecepcion", "selloRecibido")
        ?? dteValue(sale, "selloRecepcion", "selloRecibido");

    setTicketText("#ticket-document-name", sale.tipoComprobante === "CREDITO_FISCAL"
        ? "COMPROBANTE DE CRÉDITO FISCAL" : "FACTURA DE CONSUMIDOR FINAL");
    setTicketText("#ticket-cod-gen", code ?? "SIMULADO-UUID-0000");
    setTicketText("#ticket-num-control", control ?? "DTE-01-S01P01-00000001");
    const selloElement = document.querySelector("#ticket-sello-rec");
    selloElement.textContent = contingency
        ? "CONTINGENCIA - PENDIENTE DE TRANSMISIÓN MH"
        : (sello ?? "AUTORIZADO (MODO PRUEBAS)");
    selloElement.style.color = contingency ? "#d97706" : "#000";

    setTicketText("#ticket-date", sale.fecha ? formatShiftDate(sale.fecha) : new Date().toLocaleString());
    setTicketText("#ticket-folio", sale.folio ?? sale.folioInterno ?? sale.numeroOrden);
    setTicketText("#ticket-cashier", sale.cajeroNombre ?? "Cajero General");
    setTicketText("#ticket-customer", sale.clienteNombre ?? sale.compradorNombre ?? "Consumidor Final");
    setTicketText("#ticket-subtotal", money(sale.subtotal));
    setTicketText("#ticket-tax", money(sale.iva));
    setTicketText("#ticket-discount", `-${money(sale.descuento ?? 0)}`);
    setTicketText("#ticket-grand-total", money(total));
    setTicketText("#ticket-pay-method", sale.metodoPago ?? "Efectivo");
    const totalPaid = Number(sale.totalPagado ?? sale.efectivoRecibido ?? total);
    const change = Math.max(0, Number((totalPaid - total).toFixed(2)));
    setTicketText("#ticket-cash-given", money(totalPaid));
    setTicketText("#ticket-change", money(change));
    document.querySelector("#ticket-discount-row").classList.toggle("hidden", !(sale.descuento > 0));
    document.querySelector("#ticket-contingency-status").classList.toggle("hidden", !contingency);

    document.querySelector("#ticket-body-items").innerHTML = details.map(item => {
        const quantity = Number(item.cantidad ?? 0);
        const unitPrice = Number(item.precioUnitario ?? 0);
        const lineTotal = Number(item.subtotal ?? quantity * unitPrice);
        return `<tr><td class="col-qty">${quantity}</td>
            <td class="col-desc">${escapeHtml(item.productoNombre ?? item.nombreProducto ?? "")}</td>
            <td class="col-price">${money(unitPrice)}</td><td class="col-total">${money(lineTotal)}</td></tr>`;
    }).join("");
}

function openTicketModal(sale = currentSale, dte = null) {
    if (!sale) return;
    populateThermalTicket(sale, dte);
    document.querySelector("#ticket-modal").style.display = "flex";
}

function openAndPrintSaleTicket(saleResponse) {
    openTicketModal(saleResponse);
}

function closeTicketModal() {
    document.querySelector("#ticket-modal").style.display = "none";
}

function printTicket() {
    document.body.classList.add("thermal-ticket-print");
    window.print();
}
const originalBodyOverflow = document.body.style.overflow;

function setPosViewport(active) {
    document.body.classList.toggle("pos-viewport", active);
    document.body.style.overflow = active ? "hidden" : originalBodyOverflow;
}

setPosViewport(false);

const elements = {
    grid: document.querySelector("#product-grid"),
    search: document.querySelector("#product-search"),
    count: document.querySelector("#product-count"),
    allCount: document.querySelector("#all-count"),
    emptyState: document.querySelector("#empty-state"),
    cartItems: document.querySelector("#cart-items"),
    subtotal: document.querySelector("#subtotal"),
    tax: document.querySelector("#tax-total"),
    total: document.querySelector("#grand-total"),
    checkoutTotal: document.querySelector("#checkout-total"),
    checkoutButton: document.querySelector("#checkout-button"),
    orderNumber: document.querySelector("#order-number"),
    dialog: document.querySelector("#checkout-dialog"),
    dialogTotal: document.querySelector("#dialog-total"),
    cashField: document.querySelector("#cash-field"),
    cashInput: document.querySelector("#cash-received"),
    changeLine: document.querySelector("#change-line"),
    changeTotal: document.querySelector("#change-total"),
    paymentRemaining: document.querySelector("#payment-remaining"),
    partialPayments: document.querySelector("#partial-payments"),
    addPartialPayment: document.querySelector("#add-partial-payment"),
    error: document.querySelector("#dialog-error"),
    toast: document.querySelector("#toast"),
    settingsView: document.querySelector("#settings-view"),
    inventoryWorkspace: document.querySelector("#inventory-workspace"),
    productsAdmin: document.querySelector("#products-admin"),
    adminProductsBody: document.querySelector("#admin-products-body"),
    adminProductsEmpty: document.querySelector("#admin-products-empty"),
    adminSearch: document.querySelector("#admin-product-search"),
    adminCategory: document.querySelector("#admin-category-filter"),
    productDialog: document.querySelector("#product-dialog"),
    productForm: document.querySelector("#product-form"),
    productFormError: document.querySelector("#product-form-error"),
    inventoryAdmin: document.querySelector("#inventory-admin"),
    inventoryProductsBody: document.querySelector("#inventory-products-body"),
    inventoryProductsEmpty: document.querySelector("#inventory-products-empty"),
    inventoryMovementsBody: document.querySelector("#inventory-movements-body"),
    inventoryMovementsEmpty: document.querySelector("#inventory-movements-empty"),
    inventoryAdjustDialog: document.querySelector("#inventory-adjust-dialog"),
    inventoryAdjustForm: document.querySelector("#inventory-adjust-form"),
    inventoryAdjustError: document.querySelector("#inventory-adjust-error"),
    inventoryProductSelect: document.querySelector("#inventory-product-select"),
    salesAdmin: document.querySelector("#sales-admin"),
    customersAdmin: document.querySelector("#customers-admin"),
    purchasesAdmin: document.querySelector("#purchases-admin"),
    salesTableBody: document.querySelector("#sales-table-body"),
    salesEmpty: document.querySelector("#sales-empty"),
    historyDialog: document.querySelector("#history-dialog"),
    reportsAdmin: document.querySelector("#reports-admin")
};

function money(amount) {
    return `${currencySymbol}${decimalFormat.format(amount)}`;
}

function formatShiftDate(date) {
    return date ? new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(date)) : "—";
}

function durationLabel(start, end = new Date()) {
    const minutes = Math.max(0, Math.floor((new Date(end) - new Date(start)) / 60000));
    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;
    return hours ? `${hours} h ${remainder} min` : `${remainder} min`;
}

function renderShiftIndicator() {
    const indicator = document.querySelector("#shift-indicator");
    const label = document.querySelector("#shift-indicator-label");
    if (!currentShift) {
        label.textContent = "Turno cerrado · Abrir caja";
        indicator.setAttribute("aria-label", "Abrir turno de caja");
        indicator.classList.remove("shift-open");
        return;
    }
    label.textContent = `Turno abierto · ${money(currentShift.efectivoEsperado)}`;
    indicator.setAttribute("aria-label", `Turno abierto. Efectivo esperado ${money(currentShift.efectivoEsperado)}. Cerrar y auditar turno`);
    indicator.classList.add("shift-open");
}

async function loadCurrentShift(promptToOpen = false) {
    try {
        currentShift = await api("/api/v1/turnos/activo");
        renderShiftIndicator();
        if (promptToOpen && !currentShift) openShiftOpeningDialog();
    } catch (error) {
        showToast(`No se pudo consultar el turno de caja: ${error.message}`);
    }
}

function openShiftOpeningDialog() {
    const dialog = document.querySelector("#shift-dialog");
    if (!dialog.open) {
        document.querySelector("#shift-dialog-title").textContent = "Abrir turno";
        document.querySelector("#shift-dialog-eyebrow").textContent = "CONTROL DE CAJA";
        document.querySelector("#shift-open-form").classList.remove("hidden");
        document.querySelector("#shift-audit").classList.add("hidden");
        document.querySelector("#shift-open-error").classList.add("hidden");
        document.querySelector("#shift-opening-cash").value = "";
        document.querySelector("#shift-open-form [name='observaciones']").value = "";
        dialog.showModal();
        document.querySelector("#shift-opening-cash").focus();
    }
}

function openShiftAudit(turno = currentShift) {
    if (!turno) return openShiftOpeningDialog();
    const dialog = document.querySelector("#shift-dialog");
    document.querySelector("#shift-dialog-title").textContent =
        turno.estado === "CLOSED" ? "Corte Z · Turno cerrado" : "Cierre y auditoría de caja";
    document.querySelector("#shift-dialog-eyebrow").textContent =
        turno.estado === "CLOSED" ? "RESUMEN DE CIERRE" : "CONTROL DE CAJA";
    document.querySelector("#shift-open-form").classList.add("hidden");
    document.querySelector("#shift-audit").classList.remove("hidden");
    document.querySelector("#shift-close-error").classList.add("hidden");
    const isClosed = turno.estado === "CLOSED";
    document.querySelector("#shift-audit-details").innerHTML = `
        <div class="shift-audit-meta">
            <p><span>Cajero</span><strong>${escapeHtml(turno.cajero)}</strong></p>
            <p><span>Turno</span><strong>#${escapeHtml(turno.id)}</strong></p>
            <p><span>Apertura</span><strong>${escapeHtml(formatShiftDate(turno.fechaApertura))}</strong></p>
            ${isClosed ? `<p><span>Cierre</span><strong>${escapeHtml(formatShiftDate(turno.fechaCierre))}</strong></p>` : ""}
            <p><span>Duración</span><strong>${escapeHtml(durationLabel(turno.fechaApertura, turno.fechaCierre || new Date()))}</strong></p>
        </div>
        <div class="shift-audit-lines">
            <p><span>Efectivo inicial</span><strong>${money(turno.montoInicial)}</strong></p>
            <p><span>Ventas en efectivo</span><strong>+ ${money(turno.ventasEfectivo)}</strong></p>
            <p><span>Devoluciones en efectivo</span><strong>− ${money(turno.devolucionesEfectivo)}</strong></p>
            <p class="shift-expected"><span>Efectivo esperado</span><strong>${money(turno.efectivoEsperado)}</strong></p>
            ${isClosed ? `<p><span>Efectivo contado</span><strong>${money(turno.efectivoReal)}</strong></p><p class="shift-variance-line"><span>Variación</span><strong>${money(turno.variacion)}</strong></p>` : ""}
        </div>
        ${turno.observaciones ? `<p class="shift-observations"><strong>Observaciones:</strong> ${escapeHtml(turno.observaciones)}</p>` : ""}`;
    document.querySelector("#shift-counted-field").classList.toggle("hidden", isClosed);
    document.querySelector("#shift-close-observations-field").classList.toggle("hidden", isClosed);
    document.querySelector("#shift-live-variance").classList.toggle("hidden", isClosed);
    document.querySelector("#shift-close-actions").classList.toggle("hidden", isClosed);
    document.querySelector("#shift-closed-actions").classList.toggle("hidden", !isClosed);
    document.querySelector("#shift-counted-cash").value = "";
    document.querySelector("#shift-close-observations").value = "";
    if (!dialog.open) dialog.showModal();
    if (!isClosed) document.querySelector("#shift-counted-cash").focus();
}

function updateShiftVariance() {
    const field = document.querySelector("#shift-counted-cash");
    const variance = document.querySelector("#shift-live-variance");
    if (!currentShift || field.value === "") {
        variance.textContent = "Diferencia: —";
        return;
    }
    const amount = Number(field.value);
    if (!Number.isFinite(amount) || amount < 0) {
        variance.textContent = "Ingresa un monto válido.";
        return;
    }
    variance.textContent = `Diferencia: ${money(amount - Number(currentShift.efectivoEsperado))}`;
}

function cents(amount) {
    return Math.round((amount + Number.EPSILON) * 100);
}

function precioBaseUnitarioCentavos(product) {
    const precio = cents(product.precio);
    return product.precioIncluyeIva ? Math.round(precio / (1 + ivaRate)) : precio;
}

function ivaUnitarioCentavos(product) {
    const base = precioBaseUnitarioCentavos(product);
    const final = product.precioIncluyeIva
        ? cents(product.precio)
        : base + Math.round(base * ivaRate);
    return final - base;
}

function precioFinalUnitarioCentavos(product) {
    return precioBaseUnitarioCentavos(product) + ivaUnitarioCentavos(product);
}

function calcularImporteDevolucionEstimado(sale, items) {
    const subtotalDevuelto = items.reduce((sum, item) => {
        const detalle = sale.detalles.find(detail => detail.productoId === item.productoId);
        return sum + cents(detalle.precioUnitario) * item.cantidad;
    }, 0);
    const unidadesRestantes = sale.detalles.reduce(
        (sum, detail) => sum + detail.cantidad - (detail.cantidadDevuelta ?? 0), 0);
    const unidadesSeleccionadas = items.reduce((sum, item) => sum + item.cantidad, 0);
    const yaDevuelto = (sale.devoluciones ?? []).reduce((sum, item) => sum + cents(item.importe), 0);
    if (unidadesSeleccionadas === unidadesRestantes) return (cents(sale.total) - yaDevuelto) / 100;

    const importeBruto = sale.detalles.reduce((sum, detail) => sum + cents(detail.subtotal), 0);
    if (importeBruto <= 0) return 0;
    return Math.min(cents(sale.total) - yaDevuelto, Math.round(cents(sale.total) * subtotalDevuelto / importeBruto)) / 100;
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        credentials: "same-origin",
        headers: {
            ...(options.body && !(options.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
            ...options.headers
        }
    });

    if (response.redirected && new URL(response.url).pathname === "/login") {
        throw new Error("Tu sesión venció. Inicia sesión nuevamente.");
    }

    if (!response.ok) {
        let message = response.status === 401
            ? "Tu sesión venció. Inicia sesión nuevamente."
            : `No se pudo completar la solicitud (${response.status}).`;
        try {
            const body = await response.json();
            if (body.message) message = body.message;
        } catch {
            // Conserva el error HTTP si el servidor no devolvió JSON.
        }
        throw new Error(message);
    }

    return response.status === 204 ? null : response.json();
}

function productIllustration(product) {
    const color = artwork[product.nombre]?.[1] ?? "#98745b";
    return `<svg viewBox="0 0 80 80" fill="none" aria-hidden="true">
        <ellipse cx="40" cy="68" rx="22" ry="4" fill="#634B3C" opacity=".14"/>
        <path d="M22 33h34l-3.1 24.2a5 5 0 0 1-5 4.4H30.1a5 5 0 0 1-5-4.4L22 33Z" fill="${color}"/>
        <ellipse cx="39" cy="33" rx="17" ry="5.5" fill="#FFF8EC"/>
        <ellipse cx="39" cy="33" rx="13" ry="3.3" fill="#76503D"/>
        <ellipse cx="39" cy="32.5" rx="7" ry="2.1" fill="#B78667"/>
        <path d="M56 39h3.5a7.5 7.5 0 0 1 0 15H54" stroke="#FFF8EC" stroke-width="4" stroke-linecap="round"/>
        <path d="M31 38c-2 4-2 8-1 12" stroke="#FFF8EC" stroke-opacity=".42" stroke-width="2" stroke-linecap="round"/>
        <path d="M33 20c-4-4 4-5 0-9m9 9c-4-4 4-5 0-9" stroke="#FFF8EC" stroke-width="2" stroke-linecap="round" opacity=".8"/>
    </svg>`;
}

function renderProducts() {
    const query = elements.search.value.trim().toLocaleLowerCase("es");
    const filteredProducts = products.filter(product =>
        (activeCategory === "Todos" || product.categoria === activeCategory)
        && product.nombre.toLocaleLowerCase("es").includes(query)
    );
    const visibleProducts = filteredProducts.slice(0, 8);

    elements.grid.innerHTML = visibleProducts.map(product => {
        const [artStyle] = artwork[product.nombre] ?? ["coffee"];
        const name = escapeHtml(product.nombre);
        const category = escapeHtml(product.categoria);
        return `
        <article class="product-card" tabindex="0" data-add-product="${product.id}" aria-label="Agregar ${name}, ${money(precioFinalUnitarioCentavos(product) / 100)}">
            <div class="product-art art-${artStyle}">${productIllustration(product)}</div>
            <div class="product-info">
                <p class="product-category">${category}</p>
                <h3 title="${name}">${name}</h3>
                <div class="product-bottom"><span class="product-price">${money(precioFinalUnitarioCentavos(product) / 100)}</span>
                    <button class="add-product" type="button" aria-label="Agregar ${name}" ${product.stock <= 0 ? "disabled" : ""}>${product.stock <= 0 ? "−" : "+"}</button>
                </div>
            </div>
        </article>`;
    }).join("");
    elements.count.textContent = `${visibleProducts.length} productos`;
    elements.allCount.textContent = products.length;
    elements.emptyState.classList.toggle("hidden", visibleProducts.length > 0);
    if (products.length === 0) {
        elements.emptyState.textContent = "No hay productos disponibles.";
        elements.emptyState.classList.remove("hidden");
    } else {
        elements.emptyState.textContent = "No encontramos productos. Prueba con otra búsqueda.";
    }
}

function productIcon(category) {
    const normalized = category.toLocaleLowerCase("es");
    if (normalized.includes("café") || normalized.includes("cafe")) return "☕";
    if (normalized.includes("pan")) return "🥐";
    if (normalized.includes("postre") || normalized.includes("pastel")) return "🍰";
    if (normalized.includes("bebida") || normalized.includes("té") || normalized.includes("te")) return "🧋";
    return "◇";
}

function renderAdminProducts() {
    const query = elements.adminSearch.value.trim().toLocaleLowerCase("es");
    const category = elements.adminCategory.value;
    const visibleProducts = adminProducts.filter(product =>
        (!category || product.categoria === category)
        && (!query || `${product.nombre} ${product.categoria}`.toLocaleLowerCase("es").includes(query))
    );

    elements.adminProductsBody.innerHTML = visibleProducts.map(product => `
        <tr>
            <td><span class="admin-product-name"><span class="admin-product-icon" aria-hidden="true">${productIcon(product.categoria)}</span>${escapeHtml(product.nombre)}</span></td>
            <td>${escapeHtml(product.categoria)}</td>
            <td>${money(product.precio)}<small class="product-tax-mode">${product.precioIncluyeIva ? "IVA incluido" : "Más IVA"}</small></td>
            <td><span class="stock-badge${product.stock < 5 ? " low" : ""}">${product.stock} ${product.stock === 1 ? "unidad" : "unidades"}${product.stock < 5 ? " · Bajo" : ""}</span></td>
            <td><span class="product-status${product.activo ? "" : " inactive"}">${product.activo ? "Activo" : "Inactivo"}</span></td>
            <td><span class="product-row-actions">
                <button type="button" data-product-action="edit" data-product-id="${product.id}">Editar</button>
                <button class="delete-product-button" type="button" data-product-action="delete" data-product-id="${product.id}">${product.activo ? "Desactivar" : "Eliminar"}</button>
            </span></td>
        </tr>`).join("");
    elements.adminProductsEmpty.classList.toggle("hidden", visibleProducts.length > 0);
}

async function loadAdminProducts() {
    const [loadedProducts, categories] = await Promise.all([
        api("/api/v1/productos"),
        api("/api/v1/categorias")
    ]);
    adminProducts = loadedProducts;
    products = loadedProducts.filter(product => product.activo);
    for (const [id, item] of cart) {
        const currentProduct = products.find(product => product.id === id);
        if (!currentProduct || currentProduct.stock === 0) {
            cart.delete(id);
        } else {
            item.product = currentProduct;
            item.quantity = Math.min(item.quantity, currentProduct.stock);
        }
    }

    const selectedCategory = elements.adminCategory.value;
    const productCategories = [...new Set([
        ...categories,
        ...adminProducts.map(product => product.categoria)
    ])].sort((left, right) => left.localeCompare(right, "es"));
    elements.adminCategory.innerHTML = '<option value="">Todas</option>' + productCategories
        .map(category => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`)
        .join("");
    if (productCategories.includes(selectedCategory)) elements.adminCategory.value = selectedCategory;
    document.querySelector("#product-categories").innerHTML = categories
        .map(category => `<option value="${escapeHtml(category)}"></option>`)
        .join("");
    renderAdminProducts();
    renderProducts();
    renderCart();
}

async function showInventoryTab(tab) {
    if (!isAdmin) {
        showToast("Solo los administradores pueden consultar Inventario & Catálogo.");
        return;
    }
    document.querySelector(".workspace").classList.add("hidden");
    elements.inventoryWorkspace.classList.remove("hidden");
    elements.salesAdmin.classList.add("hidden");
    elements.customersAdmin.classList.add("hidden");
    elements.purchasesAdmin.classList.add("hidden");
    elements.reportsAdmin.classList.add("hidden");
    elements.settingsView.classList.add("hidden");
    document.querySelector("#current-page-title").textContent = "Inventario & Catálogo";
    document.querySelectorAll(".nav-link[data-module]").forEach(link => {
        link.classList.toggle("active", link.id === "inventory-nav-link");
    });
    document.querySelectorAll("[data-inventory-tab]").forEach(button => {
        const selected = button.dataset.inventoryTab === tab;
        button.classList.toggle("active", selected);
        button.setAttribute("aria-selected", String(selected));
    });
    elements.productsAdmin.classList.toggle("hidden", tab !== "catalogo");
    elements.inventoryAdmin.classList.toggle("hidden", tab === "catalogo");
    document.querySelector("#inventory-stock-section").classList.toggle("hidden", tab !== "existencias");
    document.querySelector("#inventory-history-section").classList.toggle("hidden", tab !== "kardex");
    try {
        if (tab === "catalogo") await loadAdminProducts();
        else await loadInventory();
    } catch (error) {
        showToast(error.message);
    }
}

function ocultarSeccionesPrincipales() {
    setPosViewport(false);
    document.querySelector(".workspace").classList.add("hidden");
    elements.inventoryWorkspace.classList.add("hidden");
    elements.productsAdmin.classList.add("hidden");
    elements.inventoryAdmin.classList.add("hidden");
    elements.salesAdmin.classList.add("hidden");
    elements.customersAdmin.classList.add("hidden");
    elements.purchasesAdmin.classList.add("hidden");
    elements.reportsAdmin.classList.add("hidden");
    elements.settingsView.classList.add("hidden");
}

async function loadInventory() {
    const [loadedProducts, movements] = await Promise.all([
        api("/api/v1/productos"),
        api("/api/v1/inventario/movimientos")
    ]);
    const activeProducts = loadedProducts.filter(product => product.activo);
    elements.inventoryProductsBody.innerHTML = activeProducts.map(product => `
        <tr>
            <td><span class="admin-product-name"><span class="admin-product-icon" aria-hidden="true">${productIcon(product.categoria)}</span>${escapeHtml(product.nombre)}</span></td>
            <td>${escapeHtml(product.categoria)}</td>
            <td><span class="stock-badge${product.stock < 5 ? " low" : ""}">${product.stock} ${product.stock === 1 ? "unidad" : "unidades"}${product.stock < 5 ? " · Bajo" : ""}</span></td>
            <td><button class="inventory-adjust-button" type="button" data-inventory-adjust="${product.id}">Ajustar stock</button></td>
        </tr>`).join("");
    elements.inventoryProductsEmpty.classList.toggle("hidden", activeProducts.length > 0);
    elements.inventoryProductSelect.innerHTML = activeProducts
        .map(product => `<option value="${product.id}">${escapeHtml(product.nombre)} · ${product.stock} disponibles</option>`)
        .join("");

    elements.inventoryMovementsBody.innerHTML = movements.map(movement => `
        <tr>
            <td title="${escapeHtml(movement.folioReferencia ?? movement.motivo)}">${escapeHtml(movement.folioMovimiento)}</td>
            <td>${escapeHtml(movement.producto)}</td>
            <td>${escapeHtml(movement.tipo)} · ${escapeHtml(movement.tipoOperacion)}</td>
            <td>${movement.cantidad}</td>
            <td>${movement.stockAnterior}</td>
            <td>${movement.stockNuevo}</td>
            <td>${new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(movement.fecha))}</td>
            <td>${escapeHtml(movement.usuario ?? "Sistema")}</td>
        </tr>`).join("");
    elements.inventoryMovementsEmpty.classList.toggle("hidden", movements.length > 0);
    products = activeProducts;
    for (const [id, item] of cart) {
        const currentProduct = products.find(product => product.id === id);
        if (!currentProduct || currentProduct.stock === 0) {
            cart.delete(id);
        } else {
            item.product = currentProduct;
            item.quantity = Math.min(item.quantity, currentProduct.stock);
        }
    }
    renderProducts();
    renderCart();
}

function showSalesView() {
    setPosViewport(false);
    elements.inventoryWorkspace.classList.add("hidden");
    elements.productsAdmin.classList.add("hidden");
    elements.inventoryAdmin.classList.add("hidden");
    elements.reportsAdmin.classList.add("hidden");
    elements.settingsView.classList.add("hidden");
    elements.customersAdmin.classList.add("hidden");
    elements.purchasesAdmin.classList.add("hidden");
    document.querySelector(".workspace").classList.add("hidden");
    elements.salesAdmin.classList.remove("hidden");
    document.querySelector("#current-page-title").textContent = "Ventas";
    document.querySelectorAll(".nav-link[data-module]").forEach(link => {
        link.classList.toggle("active", link.id === "sales-nav-link");
    });
    salesPage = 0;
    loadSalesPage();
}

async function showCustomersView() {
    ocultarSeccionesPrincipales();
    elements.customersAdmin.classList.remove("hidden");
    document.querySelector("#current-page-title").textContent = "Clientes";
    document.querySelectorAll(".nav-link[data-module]").forEach(link => {
        link.classList.toggle("active", link.id === "customers-nav-link");
    });
    await loadClientes();
}

async function showPurchasesView(tab = "registro") {
    if (!isAdmin) {
        showToast("Solo los administradores pueden consultar Compras.");
        return;
    }
    ocultarSeccionesPrincipales();
    elements.purchasesAdmin.classList.remove("hidden");
    document.querySelector("#current-page-title").textContent = "Compras";
    document.querySelectorAll(".nav-link[data-module]").forEach(link => {
        link.classList.toggle("active", link.id === "purchases-nav-link");
    });
    await showPurchaseTab(tab);
}

async function showPurchaseTab(tab) {
    document.querySelectorAll("[data-purchase-tab]").forEach(button => {
        const selected = button.dataset.purchaseTab === tab;
        button.classList.toggle("active", selected);
        button.setAttribute("aria-selected", String(selected));
    });
    document.querySelector("#purchase-register-panel").classList.toggle("hidden", tab !== "registro");
    document.querySelector("#purchase-history-panel").classList.toggle("hidden", tab !== "historial");
    document.querySelector("#purchase-suppliers-panel").classList.toggle("hidden", tab !== "proveedores");
    try {
        if (tab === "registro") await loadPurchaseFormData();
        if (tab === "historial") await loadPurchaseHistory();
        if (tab === "proveedores") await loadProveedores();
    } catch (error) {
        showToast(error.message);
    }
}

async function loadPurchaseFormData() {
    const [activeSuppliers, loadedProducts] = await Promise.all([
        api("/api/v1/proveedores"),
        api("/api/v1/productos")
    ]);
    purchaseProducts = loadedProducts.filter(product => product.activo);
    const selectedSupplier = document.querySelector("#purchase-supplier").value;
    document.querySelector("#purchase-supplier").innerHTML = '<option value="">Selecciona un proveedor</option>'
        + activeSuppliers.map(supplier => `<option value="${supplier.id}">${escapeHtml(supplier.nombreComercial)}</option>`).join("");
    if (activeSuppliers.some(supplier => String(supplier.id) === selectedSupplier)) {
        document.querySelector("#purchase-supplier").value = selectedSupplier;
    }
    document.querySelectorAll(".purchase-line-product").forEach(select => {
        const selectedProduct = select.value;
        select.innerHTML = purchaseProductOptions();
        if (purchaseProducts.some(product => String(product.id) === selectedProduct)) {
            select.value = selectedProduct;
        }
    });
    if (!document.querySelector("#purchase-lines").children.length) addPurchaseLine();
}

function purchaseProductOptions() {
    return '<option value="">Selecciona un producto</option><option value="__new__">＋ Crear nuevo producto...</option>' + purchaseProducts
        .map(product => `<option value="${product.id}">${escapeHtml(product.nombre)} · Stock ${product.stock}</option>`)
        .join("");
}

function addPurchaseLine() {
    const row = document.createElement("tr");
    row.className = "purchase-line";
    row.innerHTML = `
        <td><select class="purchase-line-product" aria-label="Producto" required>${purchaseProductOptions()}</select></td>
        <td><input class="purchase-line-quantity" type="number" min="1" step="1" value="1" aria-label="Cantidad" required></td>
        <td><input class="purchase-line-cost" type="number" min="0" step="0.01" value="0.00" aria-label="Costo unitario" required></td>
        <td class="purchase-line-subtotal">${money(0)}</td>
        <td><button class="sale-action-button" type="button" data-purchase-remove aria-label="Eliminar producto">×</button></td>`;
    document.querySelector("#purchase-lines").append(row);
    renderPurchaseTotal();
    return row;
}

function renderPurchaseTotal() {
    const total = [...document.querySelectorAll(".purchase-line")].reduce((sum, row) => {
        const quantity = Number(row.querySelector(".purchase-line-quantity").value) || 0;
        const cost = Number(row.querySelector(".purchase-line-cost").value) || 0;
        const subtotal = quantity * cost;
        row.querySelector(".purchase-line-subtotal").textContent = money(subtotal);
        return sum + subtotal;
    }, 0);
    document.querySelector("#purchase-total").textContent = money(total);
}

async function loadProveedores() {
    proveedores = await api("/api/v1/proveedores?incluirInactivos=true");
    document.querySelector("#suppliers-table-body").innerHTML = proveedores.map(supplier => `
        <tr>
            <td>${escapeHtml(supplier.nombreComercial)}</td><td>${escapeHtml(supplier.razonSocial ?? "—")}</td>
            <td>${escapeHtml(supplier.nrc ?? "—")}</td><td>${escapeHtml(supplier.nit ?? "—")}</td>
            <td>${escapeHtml(supplier.telefono ?? "—")}</td><td>${escapeHtml(supplier.email ?? "—")}</td>
            <td>${escapeHtml(supplier.direccion ?? "—")}</td>
            <td><span class="user-status${supplier.activo ? "" : " inactive"}">${supplier.activo ? "Activo" : "Inactivo"}</span></td>
            <td><span class="settings-user-actions">
                <button class="sale-action-button" type="button" data-supplier-edit="${supplier.id}">Editar</button>
                ${supplier.activo ? `<button class="sale-action-button" type="button" data-supplier-disable="${supplier.id}">Deshabilitar</button>` : ""}
            </span></td>
        </tr>`).join("");
    document.querySelector("#suppliers-empty").classList.toggle("hidden", proveedores.length > 0);
}

async function loadPurchaseHistory() {
    purchaseHistory = await api("/api/v1/compras");
    document.querySelector("#purchases-table-body").innerHTML = purchaseHistory.map(purchase => `
        <tr>
            <td>${new Intl.DateTimeFormat("es-SV", { dateStyle: "short", timeStyle: "short" }).format(new Date(purchase.fecha))}</td>
            <td>${escapeHtml(purchase.folioInterno)}</td><td>${escapeHtml(purchase.proveedor)}</td>
            <td>${escapeHtml(purchase.numeroFacturaProveedor ?? "—")}</td><td>${money(purchase.total)}</td>
            <td><button class="sale-action-button" type="button" data-purchase-detail="${purchase.id}">Ver detalle</button></td>
        </tr>`).join("");
    document.querySelector("#purchases-empty").classList.toggle("hidden", purchaseHistory.length > 0);
}

function openSupplierForm(supplier = null) {
    editingSupplier = supplier;
    const form = document.querySelector("#supplier-form");
    form.reset();
    for (const field of ["nombreComercial", "razonSocial", "nrc", "nit", "telefono", "email", "direccion"]) {
        form.elements[field].value = supplier?.[field] ?? "";
    }
    document.querySelector("#supplier-dialog-title").textContent = supplier ? "Editar proveedor" : "Nuevo proveedor";
    document.querySelector("#supplier-form-error").classList.add("hidden");
    document.querySelector("#supplier-dialog").showModal();
}

function mostrarDetalleCompra(purchase) {
    document.querySelector("#purchase-detail-content").innerHTML = `
        <p><strong>Folio:</strong> ${escapeHtml(purchase.folioInterno)}</p>
        <p><strong>Proveedor:</strong> ${escapeHtml(purchase.proveedor)}</p>
        <p><strong>Factura:</strong> ${escapeHtml(purchase.numeroFacturaProveedor ?? "—")}</p>
        <p><strong>Fecha:</strong> ${new Intl.DateTimeFormat("es-SV", { dateStyle: "medium", timeStyle: "short" }).format(new Date(purchase.fecha))}</p>
        <div class="products-table-wrap"><table class="products-table">
            <thead><tr><th>Producto</th><th>Cantidad</th><th>Costo unitario</th><th>Subtotal</th></tr></thead>
            <tbody>${purchase.detalles.map(detail => `
                <tr><td>${escapeHtml(detail.producto)}</td><td>${detail.cantidad}</td>
                    <td>${money(detail.costoUnitario)}</td><td>${money(detail.subtotal)}</td></tr>`).join("")}</tbody>
        </table></div>
        <p class="purchase-detail-total"><strong>Total: ${money(purchase.total)}</strong></p>`;
    document.querySelector("#purchase-detail-dialog").showModal();
}

async function loadClientes() {
    const query = document.querySelector("#customer-search").value.trim();
    const params = new URLSearchParams();
    if (query) params.set("q", query);
    try {
        const sequence = ++customerSearchSequence;
        const result = await api(`/api/v1/clientes${params.size ? `?${params}` : ""}`);
        if (sequence !== customerSearchSequence) return;
        clientes = result;
        const body = document.querySelector("#customers-table-body");
        body.innerHTML = clientes.map(cliente => `
            <tr>
                <td>${escapeHtml(cliente.nombre)}</td>
                <td>${escapeHtml(cliente.tipoDocumento)}${cliente.documento ? ` · ${escapeHtml(cliente.documento)}` : ""}</td>
                <td>${escapeHtml(cliente.telefono ?? "—")}</td>
                <td>${escapeHtml(cliente.email ?? "—")}</td>
                <td>${escapeHtml(cliente.direccion ?? "—")}</td>
                <td><span class="user-status">${cliente.activo ? "Activo" : "Inactivo"}</span></td>
                <td><span class="settings-user-actions">
                    <button class="sale-action-button" type="button" data-customer-edit="${cliente.id}">Editar</button>
                    <button class="sale-action-button" type="button" data-customer-disable="${cliente.id}">Deshabilitar</button>
                </span></td>
            </tr>`).join("");
        document.querySelector("#customers-empty").classList.toggle("hidden", clientes.length > 0);
    } catch (error) {
        showToast(error.message);
    }
}

function openCustomerForm(cliente = null) {
    editingCustomer = cliente;
    const form = document.querySelector("#customer-form");
    form.reset();
    form.elements.nombre.value = cliente?.nombre ?? "";
    form.elements.tipoDocumento.value = cliente?.tipoDocumento ?? "DUI";
    form.elements.documento.value = cliente?.documento ?? "";
    form.elements.nrc.value = cliente?.nrc ?? "";
    form.elements.giro.value = cliente?.giro ?? "";
    form.elements.codigoActividad.value = cliente?.codigoActividad ?? "";
    document.querySelector("#activity-selection").textContent = cliente?.codigoActividad
        ? `Código de actividad: ${cliente.codigoActividad}`
        : "";
    document.querySelector("#activity-suggestions").classList.add("hidden");
    document.querySelector("#customer-giro").setAttribute("aria-expanded", "false");
    form.elements.telefono.value = cliente?.telefono ?? "";
    form.elements.email.value = cliente?.email ?? "";
    form.elements.direccion.value = cliente?.direccion ?? "";
    document.querySelector("#customer-dialog-title").textContent = cliente ? "Editar cliente" : "Nuevo cliente";
    document.querySelector("#save-customer-button").textContent = cliente ? "Guardar cambios" : "Guardar cliente";
    document.querySelector("#customer-form-error").classList.add("hidden");
    document.querySelector("#customer-dialog").showModal();
}

function renderActividadesEconomicas() {
    const list = document.querySelector("#activity-suggestions");
    list.innerHTML = actividadesEconomicas.length
        ? actividadesEconomicas.map(actividad => `
            <button type="button" class="activity-option" role="option"
                    data-activity-code="${escapeHtml(actividad.codigo)}"
                    data-activity-description="${escapeHtml(actividad.descripcion)}">
                <strong>${escapeHtml(actividad.codigo)} · ${escapeHtml(actividad.descripcion)}</strong>
            </button>`).join("")
        : '<p class="activity-empty">No hay coincidencias. Puedes conservar el giro escrito.</p>';
    list.classList.remove("hidden");
    document.querySelector("#customer-giro").setAttribute("aria-expanded", "true");
}

async function buscarActividadesEconomicas() {
    const query = document.querySelector("#customer-giro").value.trim();
    if (!query) {
        actividadesEconomicas = [];
        document.querySelector("#activity-suggestions").classList.add("hidden");
        document.querySelector("#customer-giro").setAttribute("aria-expanded", "false");
        return;
    }
    const sequence = ++activitySearchSequence;
    try {
        const params = new URLSearchParams({ q: query });
        const result = await api(`/api/v1/catalogos/actividades-economicas?${params}`);
        if (sequence !== activitySearchSequence) return;
        actividadesEconomicas = result;
        renderActividadesEconomicas();
    } catch (error) {
        showToast(error.message);
    }
}

function clienteTieneDatosFiscales(cliente) {
    return Boolean(cliente?.nrc?.trim()
        && cliente?.giro?.trim()
        && cliente?.documento?.trim()
        && ["NIT", "DUI"].includes(cliente?.tipoDocumento));
}

function actualizarRequisitosComprobante() {
    const esCreditoFiscal = receiptType === "CREDITO_FISCAL";
    const warning = document.querySelector("#fiscal-customer-warning");
    const warningText = document.querySelector("#fiscal-customer-warning-text");
    const completar = document.querySelector("#complete-fiscal-customer");
    const confirmButton = document.querySelector("#confirm-payment");
    document.querySelector("#checkout-customer-required").classList.toggle("hidden", !esCreditoFiscal);
    checkoutCustomerSearch.required = esCreditoFiscal;
    document.querySelector("#checkout-customer-search").classList.toggle("required-field", esCreditoFiscal);
    warning.classList.add("hidden");
    completar.classList.add("hidden");
    confirmButton.disabled = false;
    if (!esCreditoFiscal) return;

    if (!selectedCheckoutCustomer) {
        warningText.textContent = "Selecciona un cliente para emitir crédito fiscal.";
        warning.classList.remove("hidden");
        confirmButton.disabled = true;
        return;
    }
    if (!clienteTieneDatosFiscales(selectedCheckoutCustomer)) {
        warningText.textContent = "Faltan datos fiscales obligatorios: documento DUI/NIT, NRC y giro.";
        completar.classList.remove("hidden");
        warning.classList.remove("hidden");
        confirmButton.disabled = true;
    }
}

function renderCheckoutCustomerSuggestions() {
    const suggestions = document.querySelector("#checkout-customer-suggestions");
    suggestions.innerHTML = checkoutClientes.length
        ? checkoutClientes.map(cliente => `
            <button type="button" class="customer-suggestion" role="option" data-checkout-customer="${cliente.id}">
                <strong>${escapeHtml(cliente.nombre)}</strong>
                <span>${escapeHtml(cliente.tipoDocumento)}${cliente.documento ? ` · ${escapeHtml(cliente.documento)}` : ""}${cliente.nrc ? ` · NRC ${escapeHtml(cliente.nrc)}` : ""}</span>
            </button>`).join("")
        : '<p class="customer-suggestions-empty">No se encontraron clientes activos.</p>';
    suggestions.classList.remove("hidden");
}

async function buscarClientesParaCheckout() {
    const query = document.querySelector("#checkout-customer-search").value.trim();
    const params = new URLSearchParams();
    if (query) params.set("q", query);
    try {
        const sequence = ++checkoutCustomerSearchSequence;
        const result = await api(`/api/v1/clientes${params.size ? `?${params}` : ""}`);
        if (sequence !== checkoutCustomerSearchSequence) return;
        checkoutClientes = result;
        renderCheckoutCustomerSuggestions();
    } catch (error) {
        showToast(error.message);
    }
}

function reportDateString(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function setReportPeriod(period) {
    const today = new Date();
    let start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const end = new Date(start);
    if (period === "semana") {
        start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
    } else if (period === "mes") {
        start.setDate(1);
    }
    const custom = period === "personalizado";
    document.querySelector("#reports-date-start").disabled = !custom;
    document.querySelector("#reports-date-end").disabled = !custom;
    if (!custom) {
        document.querySelector("#reports-date-start").value = reportDateString(start);
        document.querySelector("#reports-date-end").value = reportDateString(end);
    }
}

function renderReportDonut(container, rows, { label, amount, description }) {
    const chartRows = rows
        .map(row => ({ row, value: Math.max(0, Number(amount(row)) || 0) }))
        .filter(item => item.value > 0);
    const total = chartRows.reduce((sum, item) => sum + item.value, 0);
    const colors = ["#bd7157", "#d9a441", "#718b68", "#7799a6", "#9b7aa5", "#cc8b6a", "#7183a5"];
    const radius = 76;
    const circumference = 2 * Math.PI * radius;
    let offset = 0;
    const slices = chartRows.map((item, index) => {
        const ratio = item.value / total;
        const length = ratio * circumference;
        const percentage = ratio * 100;
        const color = colors[index % colors.length];
        const title = `${label(item.row)}: ${money(item.value)} (${percentage.toFixed(1)}%)`;
        const circle = `<circle class="report-donut-slice" cx="110" cy="110" r="${radius}" fill="none" stroke="${color}" stroke-width="31" stroke-dasharray="${length} ${circumference - length}" stroke-dashoffset="${-offset}" tabindex="0" aria-label="${escapeHtml(title)}"><title>${escapeHtml(title)}</title></circle>`;
        offset += length;
        return { circle, color, title, name: label(item.row), value: item.value, percentage };
    });
    const center = total > 0
        ? `<text class="report-donut-total" x="110" y="106" text-anchor="middle">${escapeHtml(money(total))}</text><text class="report-donut-caption" x="110" y="124" text-anchor="middle">Total</text>`
        : "";
    container.innerHTML = `<div class="report-donut-visual">
        <svg class="report-donut-svg" viewBox="0 0 220 220" role="img" aria-label="${escapeHtml(description)}">
            <circle cx="110" cy="110" r="${radius}" fill="none" stroke="#f0f1f3" stroke-width="31"></circle>
            <g transform="rotate(-90 110 110)">${slices.map(slice => slice.circle).join("")}</g>
            ${center}
        </svg>
        <ul class="report-donut-legend">${slices.map(slice => `
            <li title="${escapeHtml(slice.title)}"><span class="report-legend-swatch" style="--legend-color:${slice.color}"></span>
                <span class="report-legend-name">${escapeHtml(slice.name)}</span>
                <strong>${slice.percentage.toFixed(1)}%</strong>
                <small>${money(slice.value)}</small>
            </li>`).join("")}</ul>
    </div>`;
}

function renderReportProducts(container, rows, emptyElement) {
    container.innerHTML = rows.map((product, index) => `
        <div class="report-rank-row">
            <span class="report-rank-number">${index + 1}</span>
            <span class="report-rank-copy"><strong>${escapeHtml(product.productName)}</strong><small>${escapeHtml(product.category)} · ${product.unitsSold} unidades</small></span>
            <strong>${money(product.totalRevenue)}</strong>
        </div>`).join("");
    emptyElement.classList.toggle("hidden", rows.length > 0);
}

async function loadReports() {
    if (!isAdmin) {
        showToast("Solo los administradores pueden consultar reportes.");
        return;
    }
    const start = document.querySelector("#reports-date-start").value;
    const end = document.querySelector("#reports-date-end").value;
    if (!start || !end || start > end) {
        showToast("Selecciona un rango de fechas válido.");
        return;
    }
    const params = new URLSearchParams({
        fechaInicio: `${start}T00:00:00`,
        fechaFin: `${end}T23:59:59`
    });
    try {
        const report = await api(`/api/v1/reportes/dashboard?${params}`);
        document.querySelector("#report-total-sales").textContent = money(report.totalVentas);
        document.querySelector("#report-total-transactions").textContent =
            new Intl.NumberFormat("es-MX").format(report.totalTransacciones);
        document.querySelector("#report-average-ticket").textContent = money(report.ticketMedio);
        document.querySelector("#report-total-returns").textContent = money(report.totalDevoluciones);

        const employees = report.ventasPorEmpleado ?? [];
        document.querySelector("#report-employees").innerHTML = employees.map(employee => `
            <tr><td>${escapeHtml(employee.cashierName)}</td><td>${employee.salesCount}</td><td>${money(employee.totalAmount)}</td></tr>`
        ).join("");
        document.querySelector("#report-employees-empty").classList.toggle("hidden", employees.length > 0);

        const methods = report.ventasPorMetodoPago ?? [];
        renderReportDonut(document.querySelector("#report-payment-methods"), methods, {
            label: row => row.paymentMethod,
            amount: row => row.totalAmount,
            description: "Gráfico de dona de ventas por método de pago"
        });
        document.querySelector("#report-payments-empty").classList.toggle("hidden", methods.length > 0);

        renderReportProducts(
            document.querySelector("#report-top-products"),
            report.topProductos ?? [],
            document.querySelector("#report-top-empty"));
        renderReportProducts(
            document.querySelector("#report-low-products"),
            report.menosVendidos ?? [],
            document.querySelector("#report-low-empty"));

        const categories = report.ventasPorCategoria ?? [];
        renderReportDonut(document.querySelector("#report-categories"), categories, {
            label: row => row.categoryName,
            amount: row => row.totalAmount,
            description: "Gráfico de dona de ventas por categoría"
        });
        document.querySelector("#report-categories-empty").classList.toggle("hidden", categories.length > 0);
        reportLoaded = true;
    } catch (error) {
        showToast(error.message);
    }
}

function showReportsView() {
    if (!isAdmin) {
        showToast("Solo los administradores pueden consultar reportes.");
        showPointOfSale();
        return;
    }
    ocultarSeccionesPrincipales();
    elements.reportsAdmin.classList.remove("hidden");
    document.querySelector("#current-page-title").textContent = "Reportes";
    document.querySelectorAll(".nav-link[data-module]").forEach(link => {
        link.classList.toggle("active", link.id === "reports-nav-link");
    });
    if (!reportLoaded) setReportPeriod(document.querySelector("#reports-period").value);
    loadReports();
}

function reportExportParams(tipo) {
    const periodo = document.querySelector("#reports-period").value;
    const start = document.querySelector("#reports-date-start").value;
    const end = document.querySelector("#reports-date-end").value;
    const params = new URLSearchParams({ tipo, periodo });
    if (start) params.set("desde", `${start}T00:00:00`);
    if (end) params.set("hasta", `${end}T23:59:59`);
    return params;
}

function imprimirReporte() {
    document.body.classList.add("reports-print");
    window.print();
}

async function descargarReporte(tipo) {
    if (tipo === "pdf") {
        const reporte = document.querySelector("#reports-admin");
        if (!reporte || typeof window.html2pdf !== "function") {
            throw new Error("No se pudo cargar el exportador PDF.");
        }
        const ocultos = reporte.querySelectorAll(".reports-export-actions, .reports-controls");
        const estados = Array.from(ocultos, elemento => elemento.style.display);
        ocultos.forEach(elemento => elemento.style.display = "none");
        try {
            await window.html2pdf().set({
                margin: [0.35, 0.35, 0.35, 0.35],
                filename: "reporte-ventas.pdf",
                image: {type: "jpeg", quality: 0.98},
                html2canvas: {
                    scale: 2,
                    useCORS: true,
                    backgroundColor: "#ffffff",
                    onclone: documento => {
                        const estilo = documento.createElement("style");
                        estilo.textContent = `
                            html, body, #reports-admin {
                                background: #ffffff !important;
                                background-color: #ffffff !important;
                                color: #0f172a !important;
                            }
                            #reports-admin, #reports-admin * {
                                border-color: #e2e8f0 !important;
                                color: #1e293b !important;
                            }
                            #reports-admin .page-heading,
                            #reports-admin .report-panel,
                            #reports-admin .report-kpi,
                            #reports-admin .reports-controls,
                            #reports-admin table,
                            #reports-admin .products-table-wrap {
                                background: #ffffff !important;
                                background-color: #ffffff !important;
                            }
                            #reports-admin .report-panel {
                                border: 1px solid #e2e8f0 !important;
                                border-radius: 6px !important;
                                box-shadow: none !important;
                                break-inside: avoid;
                            }
                            #reports-admin .report-donut-text {
                                display: grid !important;
                                gap: 0 !important;
                                margin-top: 8px;
                                border: 1px solid #e2e8f0;
                            }
                            #reports-admin .report-donut-text-row {
                                display: grid;
                                grid-template-columns: minmax(0, 1fr) 90px 90px;
                                gap: 12px;
                                padding: 7px 10px;
                                border-bottom: 1px solid #e2e8f0;
                                font-size: 10px;
                            }
                            #reports-admin .report-donut-text-row:last-child {
                                border-bottom: 0;
                            }
                            #reports-admin .report-donut-text-row strong {
                                text-align: right;
                            }
                            #reports-admin .report-donut-text-header {
                                background: #f8fafc !important;
                                font-weight: 700;
                            }
                            #reports-admin .report-donut-visual {
                                display: none !important;
                            }
                            #reports-admin .report-rank-row,
                            #reports-admin .products-table-wrap {
                                break-inside: avoid;
                            }
                            #reports-admin th {
                                background: #f8fafc !important;
                                color: #1e293b !important;
                            }
                            #reports-admin .eyebrow,
                            #reports-admin .page-subtitle,
                            #reports-admin .report-kpi span,
                            #reports-admin .empty-state {
                                color: #334155 !important;
                            }
                        `;
                        documento.head.appendChild(estilo);
                        documento.querySelectorAll("#reports-admin [class]").forEach(elemento => {
                            elemento.className = Array.from(elemento.classList)
                                .filter(clase => !clase.startsWith("dark:") &&
                                    !["bg-slate-900", "bg-slate-800", "bg-gray-900", "text-white"].includes(clase))
                                .join(" ");
                        });
                        documento.querySelectorAll("#reports-admin canvas").forEach(canvas => {
                            try {
                                const imagen = documento.createElement("img");
                                imagen.src = canvas.toDataURL("image/png");
                                imagen.width = canvas.width;
                                imagen.height = canvas.height;
                                imagen.style.cssText = "display:block;max-width:100%;height:auto;";
                                canvas.replaceWith(imagen);
                            } catch (error) {
                                console.warn("No se pudo convertir un gráfico canvas para el PDF.", error);
                            }
                        });
                        documento.querySelectorAll("#reports-admin svg").forEach(svg => {
                            svg.setAttribute("xmlns", "http://www.w3.org/2000/svg");
                            svg.style.backgroundColor = "#ffffff";
                        });
                        documento.querySelectorAll("#reports-admin .report-donut-chart").forEach(chart => {
                            const visual = chart.querySelector(".report-donut-visual");
                            if (!visual) return;
                            const rows = Array.from(visual.querySelectorAll(".report-donut-legend li"));
                            const table = documento.createElement("div");
                            table.className = "report-donut-text";
                            table.innerHTML = `
                                <div class="report-donut-text-row report-donut-text-header">
                                    <span>Concepto</span><span>Porcentaje</span><span>Total</span>
                                </div>
                                ${rows.map(row => {
                                    const name = row.querySelector(".report-legend-name")?.textContent?.trim() || "";
                                    const percentage = row.querySelector("strong")?.textContent?.trim() || "";
                                    const total = row.querySelector("small")?.textContent?.trim() || "";
                                    return `<div class="report-donut-text-row">
                                        <span>${escapeHtml(name)}</span>
                                        <strong>${escapeHtml(percentage)}</strong>
                                        <strong>${escapeHtml(total)}</strong>
                                    </div>`;
                                }).join("")}`;
                            chart.replaceChildren(table);
                        });
                        documento.querySelector("#reports-admin")?.removeAttribute("data-theme");
                        documento.documentElement.removeAttribute("data-theme");
                    }
                },
                jsPDF: {unit: "in", format: "letter", orientation: "portrait"},
                pagebreak: {mode: ["css", "legacy"], avoid: [".report-kpi", ".report-panel"]}
            }).from(reporte).save();
        } finally {
            ocultos.forEach((elemento, indice) => elemento.style.display = estados[indice]);
        }
        return;
    }
    if (tipo !== "excel") return;
    const response = await fetch(`/api/v1/reportes/exportar?${reportExportParams(tipo)}`, {
        credentials: "same-origin"
    });
    if (!response.ok) {
        throw new Error(`No se pudo descargar el reporte (${response.status}).`);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "reporte-ventas.xlsx";
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

function aplicarConfiguracionVisual(configuracion) {
    systemSettings = configuracion;
    currencySymbol = configuracion.monedaSimbolo || "$";
    ivaRate = Number(configuracion.porcentajeIva) / 100;
    document.querySelector("#tax-label").textContent = `IVA (${new Intl.NumberFormat("es-MX", {
        style: "percent",
        maximumFractionDigits: 2
    }).format(ivaRate)})`;
    document.querySelector(".store-copy strong").textContent = configuracion.nombreEstablecimiento;
    document.querySelector(".store-copy small").textContent = configuracion.codigoSucursal;
    renderCart();
}

function llenarFormularioConfiguracion(configuracion) {
    const form = document.querySelector("#business-settings-form");
    for (const [key, value] of Object.entries(configuracion)) {
        if (form.elements.namedItem(key)) form.elements.namedItem(key).value = value ?? "";
    }
}

function llenarFormularioDte(configuracion) {
    const form = document.querySelector("#dte-settings-form");
    for (const [key, value] of Object.entries(configuracion)) {
        if (form.elements.namedItem(key) && !["clavePrivadaCertificado", "passwordCertificado"].includes(key)) {
            form.elements.namedItem(key).value = value ?? "";
        }
    }
    form.elements.namedItem("clavePrivadaCertificado").value = "";
    form.elements.namedItem("passwordCertificado").value = "";
    form.elements.namedItem("clavePrivadaCertificado").placeholder =
        configuracion.clavePrivadaCertificadoConfigurada ? "Clave guardada (no visible)" : "Sin configurar";
    form.elements.namedItem("passwordCertificado").placeholder =
        configuracion.passwordCertificadoConfigurado ? "Contraseña guardada (no visible)" : "Sin configurar";
}

function seleccionarPestanaConfiguracion(nombre) {
    document.querySelectorAll("[data-settings-tab]").forEach(tab => {
        const selected = tab.dataset.settingsTab === nombre;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-selected", String(selected));
        tab.tabIndex = selected ? 0 : -1;
    });
    document.querySelectorAll("[data-settings-panel]").forEach(panel => {
        panel.classList.toggle("hidden", panel.dataset.settingsPanel !== nombre);
    });
}

async function cargarUsuariosConfiguracion() {
    const usuarios = await api("/api/v1/usuarios");
    const body = document.querySelector("#settings-users-body");
    body.innerHTML = usuarios.map(usuario => {
        const esPropio = usuario.username.toLowerCase() === currentProfile?.username?.toLowerCase();
        return `
        <tr data-settings-user="${usuario.id}" data-username="${escapeHtml(usuario.username)}" data-nombre="${escapeHtml(usuario.nombreCompleto ?? "")}" data-rol="${escapeHtml(usuario.rol)}" data-activo="${usuario.activo}">
            <td>${escapeHtml(usuario.nombreCompleto)}</td>
            <td>${escapeHtml(usuario.username)}</td>
            <td>${usuario.rol === "ROLE_ADMIN" ? "Administrador" : "Cajero"}</td>
            <td><span class="user-status${usuario.activo ? "" : " inactive"}">${usuario.activo ? "Activo" : "Inactivo"}</span></td>
            <td><span class="settings-user-actions">
                <button class="sale-action-button" type="button" data-user-edit="${usuario.id}">Editar</button>
                <button class="sale-action-button" type="button" data-user-password-reset="${usuario.id}">Restablecer contraseña</button>
                <button class="sale-action-button" type="button" data-user-state="${usuario.id}"${esPropio ? ' disabled title="No puedes deshabilitar tu propia cuenta."' : ""}>${usuario.activo ? "Deshabilitar" : "Activar"}</button>
            </span></td>
        </tr>`;
    }).join("");
    document.querySelector("#settings-users-empty").classList.toggle("hidden", usuarios.length > 0);
}

async function cargarLogErrores() {
    const viewer = document.querySelector("#error-log-viewer");
    viewer.textContent = "Cargando log...";
    try {
        const lines = await api("/api/v1/logs/errores");
        viewer.textContent = Array.isArray(lines) && lines.length
            ? lines.join("\n")
            : "No hay líneas de log disponibles.";
    } catch (error) {
        viewer.textContent = `No se pudo cargar el log: ${error.message}`;
    }
}

async function cargarPromocionesConfiguracion() {
    const [promotions, availableProducts] = await Promise.all([
        api("/api/v1/promotions"),
        api("/api/v1/productos")
    ]);
    const productSelect = document.querySelector("#promotion-form").elements.productId;
    const categorySelect = document.querySelector("#promotion-form").elements.category;
    productSelect.innerHTML = availableProducts.map(product =>
        `<option value="${product.id}">${escapeHtml(product.nombre)}</option>`).join("");
    const categories = [...new Set(availableProducts.map(product => product.categoria).filter(Boolean))].sort();
    categorySelect.innerHTML = categories.map(category =>
        `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join("");
    const body = document.querySelector("#promotions-table-body");
    body.innerHTML = promotions.map(promotion => {
        const product = availableProducts.find(item => item.id === promotion.productId);
        const rule = promotion.type === "BUY_X_GET_Y"
            ? `Compra ${promotion.buyQuantity}, recibe ${promotion.freeQuantity} gratis · ${escapeHtml(product?.nombre ?? "Producto")}`
            : promotion.type === "PRODUCT_PERCENT"
                ? `${promotion.discountPercent}% · ${escapeHtml(product?.nombre ?? "Producto")}`
                : promotion.type === "CATEGORY_PERCENT"
                    ? `${promotion.discountPercent}% · ${escapeHtml(promotion.category)}`
                    : `${promotion.discountPercent}% desde ${money(promotion.minimumSpend)}`;
        const period = [promotion.startsAt, promotion.endsAt].filter(Boolean).map(formatShiftDate).join(" – ") || "Sin límite";
        return `<tr data-promotion-row="${promotion.id}">
            <td>${escapeHtml(promotion.name)}</td><td>${rule}</td><td>${escapeHtml(period)}</td>
            <td>${promotion.priority}</td><td>${promotion.active ? "Activa" : "Pausada"}</td>
            <td><span class="settings-user-actions"><button class="sale-action-button" type="button" data-promotion-edit="${promotion.id}">Editar</button>
            <button class="sale-action-button" type="button" data-promotion-delete="${promotion.id}">Eliminar</button></span></td>
        </tr>`;
    }).join("");
    document.querySelector("#promotions-empty").classList.toggle("hidden", promotions.length > 0);
    window.promotionsForEditing = promotions;
}

function actualizarCamposPromocion() {
    const form = document.querySelector("#promotion-form");
    const type = form.elements.type.value;
    const visibility = {
        product: type === "BUY_X_GET_Y" || type === "PRODUCT_PERCENT",
        category: type === "CATEGORY_PERCENT",
        buy: type === "BUY_X_GET_Y",
        free: type === "BUY_X_GET_Y",
        percent: type !== "BUY_X_GET_Y",
        minimum: type === "SPEND_TIER"
    };
    Object.entries(visibility).forEach(([field, visible]) => {
        const input = form.querySelector(`[data-promotion-field="${field}"]`);
        input.closest("label").classList.toggle("hidden", !visible);
        input.querySelector("input,select").required = visible;
    });
}

function abrirFormularioPromocion(promotion = null) {
    const form = document.querySelector("#promotion-form");
    form.reset();
    form.elements.id.value = promotion?.id ?? "";
    form.elements.name.value = promotion?.name ?? "";
    form.elements.type.value = promotion?.type ?? "BUY_X_GET_Y";
    form.elements.active.value = String(promotion?.active ?? true);
    form.elements.priority.value = promotion?.priority ?? 0;
    for (const key of ["startsAt", "endsAt"]) {
        form.elements[key].value = promotion?.[key] ? promotion[key].slice(0, 16) : "";
    }
    for (const key of ["productId", "category", "buyQuantity", "freeQuantity", "discountPercent", "minimumSpend"]) {
        if (promotion?.[key] != null) form.elements[key].value = promotion[key];
    }
    document.querySelector("#promotion-dialog-title").textContent = promotion ? "Editar promoción" : "Nueva promoción";
    document.querySelector("#promotion-form-error").classList.add("hidden");
    actualizarCamposPromocion();
    document.querySelector("#promotion-dialog").showModal();
}

async function showSettingsView() {
    ocultarSeccionesPrincipales();
    elements.settingsView.classList.remove("hidden");
    document.querySelector("#current-page-title").textContent = "Configuración";
    document.querySelectorAll(".nav-link[data-module]").forEach(link => {
        link.classList.toggle("active", link.id === "settings-nav-link");
    });
    document.querySelector("#settings-profile-name").textContent =
        currentProfile?.nombreCompleto || currentProfile?.username || "—";
    document.querySelector("#settings-profile-username").textContent = currentProfile?.username || "—";
    document.querySelector("#settings-profile-role").textContent =
        currentProfile?.rol === "ROLE_ADMIN" ? "Administrador" : "Cajero";
    document.querySelector("#business-settings-section").classList.toggle("hidden", !isAdmin);
    document.querySelector("#user-management-section").classList.toggle("hidden", !isAdmin);
    document.querySelectorAll("[data-settings-tab]").forEach(tab => {
        tab.hidden = tab.dataset.settingsTab !== "perfil"
            && tab.dataset.settingsTab !== "logs" && !isAdmin;
    });
    seleccionarPestanaConfiguracion("perfil");
    document.querySelector("#ticket-print-preference").checked =
        localStorage.getItem("vento.autoPrintTicket") === "true";
    aplicarPreferenciasVisuales();
    try {
        const configuracion = await api("/api/v1/configuracion");
        aplicarConfiguracionVisual(configuracion);
        if (isAdmin) {
            llenarFormularioConfiguracion(configuracion);
            llenarFormularioDte(configuracion);
            await Promise.all([cargarUsuariosConfiguracion(), cargarPromocionesConfiguracion()]);
        }
    } catch (error) {
        showToast(error.message);
    }
}

async function loadSalesPage() {
    const params = new URLSearchParams({ page: String(salesPage), size: "20" });
    const folio = document.querySelector("#sales-folio-filter").value.trim();
    const dateStart = document.querySelector("#sales-date-start").value;
    const dateEnd = document.querySelector("#sales-date-end").value;
    const usuarioId = document.querySelector("#sales-user-filter").value;
    if (folio) params.set("folioInterno", folio);
    if (dateStart) params.set("fechaInicio", dateStart.length === 16 ? `${dateStart}:00` : dateStart);
    if (dateEnd) params.set("fechaFin", dateEnd.length === 16 ? `${dateEnd}:59` : dateEnd);
    if (usuarioId) params.set("usuarioId", usuarioId);
    try {
        const pageData = await api(`/api/v1/ventas?${params}`);
        const sales = pageData.content ?? [];
        salesPageCount = pageData.totalPages ?? 0;
        elements.salesTableBody.innerHTML = sales.map(sale => `
            <tr>
                <td>${escapeHtml(sale.folioInterno ?? sale.numeroOrden)}</td>
                <td>${new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(sale.fecha))}</td>
                <td>${sale.detalles.reduce((count, item) => count + item.cantidad, 0)}</td>
                <td>${money(sale.total)}</td>
                <td><span class="sale-status${sale.estado !== "COMPLETADA" ? " returned" : ""}">${escapeHtml(sale.estado)}</span></td>
                <td><button class="sale-action-button" type="button" data-sale-detail="${sale.id}">Ver ticket</button></td>
            </tr>`).join("");
        elements.salesEmpty.classList.toggle("hidden", sales.length > 0);
        document.querySelector("#sales-page-summary").textContent =
            `Página ${sales.length ? salesPage + 1 : 0} de ${salesPageCount} · ${pageData.totalElements ?? 0} ventas`;
        document.querySelector("#sales-previous-page").disabled = pageData.first ?? true;
        document.querySelector("#sales-next-page").disabled = pageData.last ?? true;
    } catch (error) {
        showToast(error.message);
    }
}

async function openSaleDetails(saleId) {
    try {
        currentSale = await api(`/api/v1/ventas/${saleId}`);
        const sale = currentSale;
        document.querySelector("#history-title").textContent = `Ticket ${sale.folioInterno ?? sale.numeroOrden}`;
        document.querySelector("#ticket-metadata").innerHTML = `
            <span>Fecha: ${new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(sale.fecha))}</span>
            <span>Estado: ${escapeHtml(sale.estado)}</span>
            <span>Comprobante: ${sale.tipoComprobante === "CREDITO_FISCAL" ? "Crédito Fiscal (CCF)" : "Consumidor Final"}</span>
            <span>Cliente: ${escapeHtml(sale.clienteNombre ?? sale.compradorNombre ?? "Cliente General / Consumidor Final")}</span>
            ${sale.tipoComprobante === "CREDITO_FISCAL" ? `
                    <span>Documento: ${escapeHtml(sale.clienteDocumento ?? "—")}</span>
                <span>NRC: ${escapeHtml(sale.clienteNrc ?? "—")}</span>
                    <span>Giro: ${escapeHtml(sale.clienteCodigoActividad ? `${sale.clienteCodigoActividad} · ` : "")}${escapeHtml(sale.clienteGiro ?? "—")}</span>` : ""}
            <span>Cajero: ${escapeHtml(sale.cajeroNombre ?? "No registrado")}</span>
            <span>Pago: ${escapeHtml(sale.metodoPago ?? "No especificado")}</span>
            <span>Orden: ${escapeHtml(sale.numeroOrden)}</span>`;
        document.querySelector("#ticket-business-name").textContent = systemSettings?.nombreEstablecimiento ?? "Café Vento";
        const direccion = [systemSettings?.direccion, systemSettings?.telefono].filter(Boolean).join(" · ");
        document.querySelector("#ticket-business-contact").textContent = direccion;
        document.querySelector("#ticket-business-footer").textContent =
            systemSettings?.piePaginaTicket ?? "¡Gracias por su compra!";
        document.querySelector("#history-items").innerHTML = sale.detalles.map(item => {
            const restante = item.cantidad - (item.cantidadDevuelta ?? 0);
            const descuentoLinea = cents(item.descuentoAutomatico ?? 0) + cents(item.descuentoManual ?? 0);
            return `<div class="ticket-item">
                <div class="ticket-item-description"><strong>${escapeHtml(item.nombreProducto)}</strong>
                    <small>${item.cantidad} × ${money(item.precioUnitario)}${descuentoLinea > 0
                        ? ` · Descuento antes de IVA: ${money(descuentoLinea / 100)}` : ""}${item.promocionAplicada
                        ? ` · ${escapeHtml(item.promocionAplicada)}` : ""}${item.cantidadDevuelta ? ` · Devueltas: ${item.cantidadDevuelta}` : ""}</small>
                </div>
                <div>${money(item.subtotal)}${canProcessReturns && restante > 0 && sale.estado !== "DEVUELTA"
                    ? `<label class="hidden">Cantidad a devolver</label><input class="ticket-return-quantity" type="number" min="0" max="${restante}" value="0" data-return-product="${item.productoId}" aria-label="Cantidad a devolver de ${escapeHtml(item.nombreProducto)}">`
                    : ""}</div>
            </div>`;
        }).join("");
        document.querySelector("#ticket-totals").innerHTML = `
            <div><span>Subtotal</span><strong>${money(sale.subtotal)}</strong></div>
            <div><span>Descuento total</span><strong>−${money(sale.descuento)}</strong></div>
            ${sale.descuentoAutomatico > 0 ? `<div><span>Promoción automática</span><strong>−${money(sale.descuentoAutomatico)}</strong></div>` : ""}
            ${sale.descuentoManual > 0 ? `<div><span>Descuento manual autorizado</span><strong>−${money(sale.descuentoManual)}${sale.descuentoAprobadoPorNombre
                ? ` · ${escapeHtml(sale.descuentoAprobadoPorNombre)}` : ""}</strong></div>` : ""}
            <div><span>IVA</span><strong>${money(sale.iva)}</strong></div>
            <div class="ticket-grand-total"><span>Total</span><strong>${money(sale.total)}</strong></div>`;
        const returns = sale.devoluciones ?? [];
        document.querySelector("#ticket-returns").innerHTML = returns.length
            ? `<strong>Devoluciones</strong>${returns.map(item => `<div>${escapeHtml(item.folioDevolucion)} · ${money(item.importe)} · ${escapeHtml(item.motivo ?? "")} · ${new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(item.fecha))}</div>`).join("")}`
            : "";
        document.querySelector("#process-return-button").classList.toggle(
            "hidden", !canProcessReturns || sale.estado === "DEVUELTA"
                || sale.detalles.every(item => item.cantidadDevuelta >= item.cantidad));
        document.querySelector("#cancel-sale-button").classList.toggle(
            "hidden", !isAdmin || sale.estado !== "COMPLETADA");
        document.querySelector("#signed-dte-result").classList.add("hidden");
        populateThermalTicket(sale);
        if (!elements.historyDialog.open) elements.historyDialog.showModal();
    } catch (error) {
        showToast(error.message);
    }
}

function showPointOfSale() {
    setPosViewport(true);
    elements.inventoryWorkspace.classList.add("hidden");
    elements.productsAdmin.classList.add("hidden");
    elements.inventoryAdmin.classList.add("hidden");
    elements.salesAdmin.classList.add("hidden");
    elements.customersAdmin.classList.add("hidden");
    elements.purchasesAdmin.classList.add("hidden");
    elements.reportsAdmin.classList.add("hidden");
    elements.settingsView.classList.add("hidden");
    document.querySelector(".workspace").classList.remove("hidden");
    document.querySelector("#current-page-title").textContent = "Punto de venta";
    document.querySelectorAll(".nav-link[data-module]").forEach(link => {
        link.classList.toggle("active", link.dataset.module === "pos");
    });
    renderProducts();
    renderCart();
}

function openProductForm(product = null) {
    editingProduct = product;
    elements.productForm.reset();
    elements.productFormError.classList.add("hidden");
    elements.productForm.elements.nombre.value = product?.nombre ?? "";
    elements.productForm.elements.categoria.value = product?.categoria ?? "";
    elements.productForm.elements.precio.value = product?.precio ?? "";
    elements.productForm.elements.stock.value = product?.stock ?? 0;
    elements.productForm.elements.codigoBarras.value = product?.codigoBarras ?? "";
    elements.productForm.elements.activo.checked = product ? product.activo : true;
    elements.productForm.elements.precioIncluyeIva.checked = product ? product.precioIncluyeIva : true;
    const quickCreate = purchaseQuickCreate && !product;
    document.querySelector("#product-dialog-eyebrow").textContent = quickCreate ? "NUEVA COMPRA" : "CATÁLOGO";
    document.querySelector("#product-dialog-title").textContent = quickCreate ? "Nuevo producto" : product ? "Editar producto" : "Nuevo producto";
    document.querySelector("#save-product-button").textContent = quickCreate ? "Crear y agregar" : product ? "Guardar cambios" : "Guardar producto";
    document.querySelector("#product-stock-field").classList.toggle("hidden", quickCreate);
    document.querySelector("#product-active-toggle").classList.toggle("hidden", quickCreate);
    document.querySelector("#product-barcode-field").classList.toggle("hidden", Boolean(product));
    elements.productDialog.showModal();
}

function totals() {
    const subtotalCents = [...cart.values()].reduce(
        (sum, item) => sum + precioBaseUnitarioCentavos(item.product) * item.quantity, 0);
    const ivaAntesDescuentoCents = [...cart.values()].reduce(
        (sum, item) => sum + ivaUnitarioCentavos(item.product) * item.quantity, 0);
    const lineDiscountCents = [...cart.values()].reduce(
        (sum, item) => sum + cents(item.manualDiscountAmount ?? 0), 0);
    const discountCents = Math.min(subtotalCents,
        cents(manualDiscountAmount) + lineDiscountCents + cents(automaticDiscountAmount));
    const taxableCents = Math.max(0, subtotalCents - discountCents);
    const taxCents = ventaExenta ? 0
        : Math.max(0, ivaAntesDescuentoCents - Math.round(discountCents * ivaRate));
    return {
        subtotal: subtotalCents / 100,
        discount: discountCents / 100,
        tax: taxCents / 100,
        total: (taxableCents + taxCents) / 100
    };
}

function renderCart() {
    if (cart.size === 0) {
        elements.cartItems.innerHTML = '<div class="cart-empty"><span class="empty-cart-icon" aria-hidden="true">＋</span><strong>Aún no hay productos</strong><span>Elige algo rico para empezar.</span></div>';
    } else {
        elements.cartItems.innerHTML = [...cart.values()].map(({ product, quantity, manualDiscountAmount: lineDiscount = 0 }) => `
            <div class="cart-item">
                <div class="cart-item-main"><p class="cart-item-name" title="${escapeHtml(product.nombre)}">${escapeHtml(product.nombre)}</p><span class="cart-item-price">${money(precioFinalUnitarioCentavos(product) / 100)} c/u</span></div>
                <div class="quantity-control" aria-label="Cantidad de ${escapeHtml(product.nombre)}">
                    <button type="button" data-quantity-change="${product.id}" data-change="-1" aria-label="Quitar una unidad de ${escapeHtml(product.nombre)}">−</button>
                    <span>${quantity}</span>
                        <button type="button" data-quantity-change="${product.id}" data-change="1" aria-label="Agregar una unidad de ${escapeHtml(product.nombre)}" ${quantity >= product.stock ? "disabled" : ""}>+</button>
                </div>
                <button class="sale-action-button cart-line-discount" type="button" data-line-discount="${product.id}">
                    ${lineDiscount > 0 ? `Descuento: −${money(lineDiscount)}` : "Descuento de línea"}
                </button>
            </div>`).join("");
    }

    const current = totals();
    elements.subtotal.textContent = money(current.subtotal);
    elements.tax.textContent = money(current.tax);
    document.querySelector("#tax-label").textContent = ventaExenta ? "Venta exenta (IVA 0%)"
        : `IVA (${new Intl.NumberFormat("es-MX", { style: "percent", maximumFractionDigits: 2 }).format(ivaRate)})`;
    elements.total.textContent = money(current.total);
    elements.checkoutTotal.innerHTML = `${money(current.total)} <span aria-hidden="true">→</span>`;
    elements.checkoutButton.disabled = cart.size === 0;
    document.querySelector("#discount-total-row").classList.toggle("hidden", current.discount === 0);
    document.querySelector("#discount-total").textContent = `−${money(current.discount)}`;
    const discountButton = document.querySelector("#discount-button");
    discountButton.textContent = totalDescuentoManual() > 0
        ? `− Quitar descuentos (${money(totalDescuentoManual())})`
        : "＋ Agregar";
    discountButton.title = automaticPromotionName ? `Promoción automática: ${automaticPromotionName}` : "";
}

async function actualizarCotizacionPromocion() {
    const sequence = ++promotionQuoteSequence;
    const items = [...cart.values()].map(({ product, quantity }) => ({ productoId: product.id, cantidad: quantity }));
    if (items.length === 0) {
        automaticDiscountAmount = 0;
        automaticPromotionName = null;
        pendingExchangeFolio = null;
        ventaExenta = false;
        renderCart();
        return true;
    }
    try {
        const quote = await api("/api/v1/promotions/quote", {
            method: "POST",
            body: JSON.stringify({ items })
        });
        if (sequence !== promotionQuoteSequence) return;
        automaticDiscountAmount = Number(quote.discount) || 0;
        automaticPromotionName = quote.promotionName || null;
        renderCart();
        updatePaymentChange();
        return true;
    } catch (error) {
        if (sequence === promotionQuoteSequence) {
            automaticDiscountAmount = 0;
            automaticPromotionName = null;
            renderCart();
        }
        return false;
    }
}

function programarCotizacionPromocion() {
    window.clearTimeout(promotionQuoteTimer);
    promotionQuoteTimer = window.setTimeout(actualizarCotizacionPromocion, 100);
}

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.classList.add("visible");
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => elements.toast.classList.remove("visible"), 4000);
}

function updatePaymentChange() {
    const change = Number(elements.cashInput.value) - montoRestante;
    const hasAmount = elements.cashInput.value !== "";
    elements.changeLine.classList.toggle("hidden", paymentMethod !== "Efectivo" || !hasAmount || change < 0);
    elements.changeTotal.textContent = money(Math.max(0, change));
}

function renderPartialPayments() {
    elements.paymentRemaining.textContent = money(montoRestante);
    elements.partialPayments.innerHTML = pagosParciales.length === 0
        ? ""
        : pagosParciales.map((pago, index) =>
            `<div class="partial-payment-row"><span>${index + 1}. ${escapeHtml(pago.metodoPago)}</span><strong>${money(pago.monto)}</strong></div>`
        ).join("");
    elements.addPartialPayment.disabled = pagosParciales.length >= 3 || montoRestante <= 0.001;
    document.querySelector("#confirm-payment").disabled = montoRestante > 0.001;
}

function resetPartialPaymentFields() {
    elements.cashInput.value = montoRestante > 0 ? montoRestante.toFixed(2) : "";
    document.querySelector("#input-card-last4").value = "";
    document.querySelector("#input-auth-code").value = "";
    document.querySelector("#input-return-folio").value = "";
    updatePaymentChange();
}

function agregarPagoParcial() {
    const amount = Number(elements.cashInput.value);
    const isCardPayment = ["Cuscatlan", "Credomatic", "Nico"].includes(paymentMethod);
    const cardLast4 = document.querySelector("#input-card-last4").value.trim();
    const authCode = document.querySelector("#input-auth-code").value.trim();
    const returnFolio = document.querySelector("#input-return-folio").value.trim();
    const esDevolucion = paymentMethod === "Devolucion" || paymentMethod === "Devolución";
    if (pagosParciales.length >= 3) {
        elements.error.textContent = "Solo puedes agregar hasta 3 pagos.";
    } else if (!Number.isFinite(amount) || amount <= 0) {
        elements.error.textContent = "Ingresa un monto de pago válido.";
    } else if (amount > montoRestante && paymentMethod !== "Efectivo") {
        elements.error.textContent = "El pago no puede superar el monto restante.";
    } else if (esDevolucion && !validarFormatoFolio(returnFolio, "DE")) {
        elements.error.textContent = "Ingresa un folio de devolución válido.";
    } else if (isCardPayment && (!/^\d{4}$/.test(cardLast4) || !authCode)) {
        elements.error.textContent = "Ingresa los últimos 4 dígitos y el código de autorización.";
    } else {
        pagosParciales.push({
            metodoPago: paymentMethod,
            monto: Number(amount.toFixed(2)),
            ultimos4Digitos: isCardPayment ? cardLast4 : null,
            codigoAutorizacion: isCardPayment ? authCode : null,
            folioDevolucion: esDevolucion ? returnFolio : null
        });
        montoRestante = Math.max(0, Number((montoRestante - amount).toFixed(2)));
        elements.error.classList.add("hidden");
        renderPartialPayments();
        resetPartialPaymentFields();
        return;
    }
    elements.error.classList.remove("hidden");
}

async function loadInitialData() {
    try {
        const profile = await api("/api/v1/auth/me");
        currentProfile = profile;
        isAdmin = profile.rol === "ROLE_ADMIN";
        canProcessReturns = isAdmin || profile.rol === "ROLE_USER";
        const allowedModules = new Set(profile.allowedModules);
        document.querySelectorAll("[data-module]").forEach(link => {
            link.hidden = !allowedModules.has(link.dataset.module);
        });
        document.querySelector("#profile-name").textContent = profile.nombreCompleto || profile.username;
        document.querySelector("#profile-role").textContent = profile.rol === "ROLE_ADMIN" ? "Administrador" : "Cajero";
        document.querySelector("#profile-avatar").textContent =
            (profile.nombreCompleto || profile.username).trim().charAt(0).toLocaleUpperCase("es");
        await loadCurrentShift(true);
    } catch (error) {
        document.querySelector("#profile-name").textContent = "Usuario no identificado";
        document.querySelector("#profile-role").textContent = "Acceso limitado";
        showToast(error.message);
    }

    try {
        const [loadedProducts, configuracion] = await Promise.all([
            api("/api/v1/pos/productos"),
            api("/api/v1/configuracion")
        ]);
        products = loadedProducts;
        aplicarConfiguracionVisual(configuracion);
        renderShiftIndicator();
        document.querySelector(".connection-status").innerHTML = '<span class="status-dot"></span> Conectado a la base de datos';
        renderProducts();
        renderCart();
    } catch (error) {
        elements.emptyState.textContent = error.message;
        elements.emptyState.classList.remove("hidden");
        document.querySelector(".connection-status").innerHTML = '<span class="status-dot connection-error"></span> Error de conexión';
        showToast(error.message);
    }
}

document.querySelector("#today-date").textContent = new Intl.DateTimeFormat("es-MX", {
    weekday: "long", day: "numeric", month: "long", year: "numeric"
}).format(new Date());
elements.orderNumber.textContent = "Nueva orden";
renderProducts();
renderCart();
aplicarPreferenciasVisuales();
loadInitialData();

elements.grid.addEventListener("click", event => {
    const card = event.target.closest("[data-add-product]");
    if (!card) return;
    const product = products.find(item => item.id === Number(card.dataset.addProduct));
    if (!product) return;
    const existing = cart.get(product.id);
    if ((existing?.quantity ?? 0) >= product.stock) {
        showToast(`No hay más existencias de ${product.nombre}.`);
        return;
    }
    if (totalDescuentoManual() > 0) {
        limpiarDescuentosManuales();
        showToast("Se quitaron los descuentos manuales por cambiar los artículos de la venta.");
    }
    cart.set(product.id, { product, quantity: (existing?.quantity ?? 0) + 1, manualDiscountAmount: 0 });
    renderCart();
    programarCotizacionPromocion();
});

elements.grid.addEventListener("keydown", event => {
    if ((event.key === "Enter" || event.key === " ") && event.target.matches(".product-card")) {
        event.preventDefault();
        event.target.querySelector(".add-product").click();
    }
});

elements.search.addEventListener("input", renderProducts);
document.querySelector(".category-list").addEventListener("click", event => {
    const button = event.target.closest("[data-category]");
    if (!button) return;
    activeCategory = button.dataset.category;
    document.querySelectorAll(".category-chip").forEach(chip => {
        const selected = chip === button;
        chip.classList.toggle("selected", selected);
        chip.setAttribute("aria-pressed", String(selected));
    });
    renderProducts();
});

elements.cartItems.addEventListener("click", event => {
    const discountButton = event.target.closest("[data-line-discount]");
    if (discountButton) {
        abrirAutorizacionDescuento("line", Number(discountButton.dataset.lineDiscount));
        return;
    }
    const button = event.target.closest("[data-quantity-change]");
    if (!button) return;
    const id = Number(button.dataset.quantityChange);
    const item = cart.get(id);
    if (!item) return;
    const quantity = item.quantity + Number(button.dataset.change);
    if (totalDescuentoManual() > 0) {
        limpiarDescuentosManuales();
        showToast("Se quitaron los descuentos manuales por cambiar las cantidades.");
    }
    if (quantity <= 0) cart.delete(id);
    else cart.set(id, { ...item, quantity, manualDiscountAmount: 0 });
    renderCart();
    programarCotizacionPromocion();
    updatePaymentChange();
});

function limpiarDescuentosManuales() {
    manualDiscountAmount = 0;
    discountAuthorizationToken = null;
    for (const item of cart.values()) item.manualDiscountAmount = 0;
}

function totalDescuentoManual() {
    return manualDiscountAmount + [...cart.values()]
        .reduce((sum, item) => sum + Number(item.manualDiscountAmount ?? 0), 0);
}

function abrirAutorizacionDescuento(target, productId = null) {
    pendingDiscountTarget = target;
    pendingDiscountProductId = productId;
    const form = document.querySelector("#discount-authorization-form");
    form.reset();
    const product = productId == null ? null : cart.get(productId)?.product;
    document.querySelector("#discount-authorization-title").textContent =
        target === "line" ? `Descuento · ${product?.nombre ?? "Producto"}` : "Descuento manual";
    document.querySelector("#discount-authorization-error").classList.add("hidden");
    document.querySelector("#discount-authorization-dialog").showModal();
}

document.querySelector("#clear-cart").addEventListener("click", () => {
    if (cart.size === 0 && manualDiscountAmount === 0) return;
    cart.clear();
    limpiarDescuentosManuales();
    automaticDiscountAmount = 0;
    automaticPromotionName = null;
    promotionQuoteSequence++;
    renderCart();
    showToast("Se vació la orden.");
});

document.querySelector("#toggle-filters").addEventListener("click", event => {
    const expanded = event.currentTarget.getAttribute("aria-expanded") === "true";
    event.currentTarget.setAttribute("aria-expanded", String(!expanded));
    document.querySelector("#category-list").classList.toggle("collapsed", expanded);
});

document.querySelector("#inventory-nav-link").addEventListener("click", event => {
    event.preventDefault();
    showInventoryTab("catalogo");
});
document.querySelector("#purchases-nav-link").addEventListener("click", event => {
    event.preventDefault();
    showPurchasesView();
});
document.querySelector("#purchases-admin").querySelector(".inventory-tabs").addEventListener("click", event => {
    const tab = event.target.closest("[data-purchase-tab]");
    if (tab) showPurchaseTab(tab.dataset.purchaseTab);
});
document.querySelector(".inventory-tabs").addEventListener("click", event => {
    const tab = event.target.closest("[data-inventory-tab]");
    if (tab) showInventoryTab(tab.dataset.inventoryTab);
});
document.querySelector("#sales-nav-link").addEventListener("click", event => {
    event.preventDefault();
    showSalesView();
});
document.querySelector("#customers-nav-link").addEventListener("click", event => {
    event.preventDefault();
    showCustomersView();
});
document.querySelector("#reports-nav-link").addEventListener("click", event => {
    event.preventDefault();
    showReportsView();
});
document.querySelector("#settings-nav-link").addEventListener("click", event => {
    event.preventDefault();
    showSettingsView();
});
document.querySelector(".settings-tabs").addEventListener("click", event => {
    const tab = event.target.closest("[data-settings-tab]");
    if (tab && !tab.hidden) {
        seleccionarPestanaConfiguracion(tab.dataset.settingsTab);
        if (tab.dataset.settingsTab === "logs") cargarLogErrores();
    }
});
document.querySelector("#refresh-error-log").addEventListener("click", cargarLogErrores);
document.querySelector("#create-promotion-button").addEventListener("click", () => abrirFormularioPromocion());
document.querySelector("#close-promotion-dialog").addEventListener("click", () => document.querySelector("#promotion-dialog").close());
document.querySelector("#cancel-promotion-form").addEventListener("click", () => document.querySelector("#promotion-dialog").close());
document.querySelector("#promotion-form").elements.type.addEventListener("change", actualizarCamposPromocion);
document.querySelector("#promotion-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#promotion-form-error");
    const id = form.elements.id.value;
    const numberOrNull = key => form.elements[key].value ? Number(form.elements[key].value) : null;
    const payload = {
        name: form.elements.name.value.trim(),
        type: form.elements.type.value,
        active: form.elements.active.value === "true",
        priority: Number(form.elements.priority.value),
        startsAt: form.elements.startsAt.value || null,
        endsAt: form.elements.endsAt.value || null,
        productId: numberOrNull("productId"),
        category: form.elements.category.value || null,
        buyQuantity: numberOrNull("buyQuantity"),
        freeQuantity: numberOrNull("freeQuantity"),
        discountPercent: numberOrNull("discountPercent"),
        minimumSpend: numberOrNull("minimumSpend")
    };
    try {
        await api(id ? `/api/v1/promotions/${id}` : "/api/v1/promotions", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        document.querySelector("#promotion-dialog").close();
        await cargarPromocionesConfiguracion();
        showToast("Promoción guardada.");
    } catch (saveError) {
        error.textContent = saveError.message;
        error.classList.remove("hidden");
    }
});
document.querySelector("#promotions-table-body").addEventListener("click", async event => {
    const edit = event.target.closest("[data-promotion-edit]");
    const remove = event.target.closest("[data-promotion-delete]");
    if (edit) {
        const promotion = (window.promotionsForEditing ?? []).find(item => item.id === Number(edit.dataset.promotionEdit));
        if (promotion) abrirFormularioPromocion(promotion);
    }
    if (remove && window.confirm("¿Eliminar esta promoción?")) {
        try {
            await api(`/api/v1/promotions/${remove.dataset.promotionDelete}`, { method: "DELETE" });
            await cargarPromocionesConfiguracion();
            showToast("Promoción eliminada.");
        } catch (error) {
            showToast(error.message);
        }
    }
});
document.querySelector("#ticket-print-preference").addEventListener("change", event => {
    localStorage.setItem("vento.autoPrintTicket", String(event.currentTarget.checked));
});
document.querySelectorAll("[data-theme-mode]").forEach(button => {
    button.addEventListener("click", () => {
        localStorage.setItem("vento.theme", button.dataset.themeMode);
        aplicarPreferenciasVisuales();
    });
});
document.querySelectorAll("[data-theme-family]").forEach(button => {
    button.addEventListener("click", () => {
        localStorage.setItem("vento.themeFamily", button.dataset.themeFamily);
        aplicarPreferenciasVisuales();
    });
});
document.querySelector("#change-password-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#change-password-error");
    error.classList.add("hidden");
    try {
        await api("/api/v1/auth/password", {
            method: "PUT",
            body: JSON.stringify(Object.fromEntries(new FormData(form)))
        });
        form.reset();
        showToast("La contraseña se actualizó correctamente.");
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    }
});
document.querySelector("#business-settings-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#business-settings-error");
    error.classList.add("hidden");
    const data = Object.fromEntries(new FormData(form));
    data.porcentajeIva = Number(data.porcentajeIva);
    try {
        const configuracion = await api("/api/v1/configuracion", {
            method: "PUT",
            body: JSON.stringify(data)
        });
        aplicarConfiguracionVisual(configuracion);
        llenarFormularioConfiguracion(configuracion);
        showToast("La configuración del negocio se guardó correctamente.");
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    }
});
document.querySelector("#dte-settings-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#dte-settings-error");
    error.classList.add("hidden");
    const data = Object.fromEntries(new FormData(form));
    try {
        const configuracion = await api("/api/v1/dte/configuracion", {
            method: "PUT",
            body: JSON.stringify(data)
        });
        llenarFormularioDte(configuracion);
        showToast("La configuración DTE se guardó correctamente.");
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    }
});
document.querySelector("#create-user-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await api("/api/v1/usuarios", {
            method: "POST",
            body: JSON.stringify(Object.fromEntries(new FormData(form)))
        });
        form.reset();
        await cargarUsuariosConfiguracion();
        showToast("La cuenta se creó correctamente.");
    } catch (error) {
        showToast(error.message);
    }
});
document.querySelector("#settings-users-body").addEventListener("click", async event => {
    const editButton = event.target.closest("[data-user-edit]");
    const passwordButton = event.target.closest("[data-user-password-reset]");
    const stateButton = event.target.closest("[data-user-state]");
    if (editButton) {
        const row = editButton.closest("[data-settings-user]");
        const form = document.querySelector("#edit-user-form");
        form.elements.namedItem("nombreCompleto").value = row.dataset.nombre;
        form.elements.namedItem("username").value = row.dataset.username;
        form.elements.namedItem("rol").value = row.dataset.rol;
        form.dataset.userId = editButton.dataset.userEdit;
        document.querySelector("#edit-user-error").classList.add("hidden");
        document.querySelector("#edit-user-dialog").showModal();
    } else if (stateButton) {
        const row = stateButton.closest("[data-settings-user]");
        const nextState = row.dataset.activo !== "true";
        const action = nextState ? "activar" : "deshabilitar";
        if (!window.confirm(`¿Deseas ${action} la cuenta de ${row.dataset.username}?`)) return;
        try {
            await api(`/api/v1/usuarios/${stateButton.dataset.userState}/estado`, {
                method: "PUT",
                body: JSON.stringify({ activo: nextState })
            });
            await cargarUsuariosConfiguracion();
            showToast(nextState ? "La cuenta se activó." : "La cuenta se deshabilitó.");
        } catch (error) {
            showToast(error.message);
        }
    } else if (passwordButton) {
        const nuevaPassword = window.prompt("Nueva contraseña (mínimo 8 caracteres):");
        if (nuevaPassword === null) return;
        if (nuevaPassword.length < 8 || nuevaPassword.length > 72) {
            showToast("La contraseña debe tener entre 8 y 72 caracteres.");
            return;
        }
        try {
            await api(`/api/v1/usuarios/${passwordButton.dataset.userPasswordReset}/password`, {
                method: "PUT",
                body: JSON.stringify({ nuevaPassword })
            });
            showToast("La contraseña se restableció correctamente.");
        } catch (error) {
            showToast(error.message);
        }
    }
});
document.querySelector("#edit-user-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#edit-user-error");
    error.classList.add("hidden");
    try {
        const data = Object.fromEntries(new FormData(form));
        await api(`/api/v1/usuarios/${form.dataset.userId}`, {
            method: "PUT",
            body: JSON.stringify(data)
        });
        document.querySelector("#edit-user-dialog").close();
        await cargarUsuariosConfiguracion();
        showToast("Los datos del usuario se actualizaron.");
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    }
});
document.querySelector("#close-edit-user-dialog").addEventListener("click", () => {
    document.querySelector("#edit-user-dialog").close();
});
document.querySelector("#cancel-edit-user").addEventListener("click", () => {
    document.querySelector("#edit-user-dialog").close();
});
document.querySelector("#reports-period").addEventListener("change", event => {
    setReportPeriod(event.currentTarget.value);
    if (event.currentTarget.value !== "personalizado" && !elements.reportsAdmin.classList.contains("hidden")) {
        loadReports();
    }
});
document.querySelector("#reports-apply").addEventListener("click", loadReports);
document.querySelector("#reports-print").addEventListener("click", imprimirReporte);
const reportsDownload = document.querySelector("#reports-download");
const reportsDownloadOptions = document.querySelector("#reports-download-options");
reportsDownload.addEventListener("click", event => {
    event.stopPropagation();
    const expanded = reportsDownload.getAttribute("aria-expanded") === "true";
    reportsDownload.setAttribute("aria-expanded", String(!expanded));
    reportsDownloadOptions.classList.toggle("hidden", expanded);
});
reportsDownloadOptions.addEventListener("click", async event => {
    const option = event.target.closest("[data-report-download]");
    if (!option) return;
    reportsDownloadOptions.classList.add("hidden");
    reportsDownload.setAttribute("aria-expanded", "false");
    try {
        await descargarReporte(option.dataset.reportDownload);
    } catch (error) {
        showToast(error.message);
    }
});
document.addEventListener("click", event => {
    if (!event.target.closest(".reports-download-menu")) {
        reportsDownloadOptions.classList.add("hidden");
        reportsDownload.setAttribute("aria-expanded", "false");
    }
});
window.addEventListener("afterprint", () => document.body.classList.remove("reports-print"));
document.querySelector("#shift-indicator").addEventListener("click", () => {
    if (currentShift) openShiftAudit();
    else openShiftOpeningDialog();
});
document.querySelector("#close-shift-dialog").addEventListener("click", () => document.querySelector("#shift-dialog").close());
document.querySelector("#cancel-shift-open").addEventListener("click", () => document.querySelector("#shift-dialog").close());
document.querySelector("#cancel-shift-close").addEventListener("click", () => document.querySelector("#shift-dialog").close());
document.querySelector("#shift-open-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#shift-open-error");
    const button = document.querySelector("#submit-shift-open");
    const data = new FormData(form);
    button.disabled = true;
    error.classList.add("hidden");
    try {
        currentShift = await api("/api/v1/turnos/apertura", {
            method: "POST",
            body: JSON.stringify({
                montoInicial: Number(data.get("montoInicial")),
                observaciones: String(data.get("observaciones") || "").trim() || null
            })
        });
        renderShiftIndicator();
        document.querySelector("#shift-dialog").close();
        showToast("Turno abierto. La caja está lista para vender.");
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
});
document.querySelector("#shift-counted-cash").addEventListener("input", updateShiftVariance);
document.querySelector("#submit-shift-close").addEventListener("click", async () => {
    const field = document.querySelector("#shift-counted-cash");
    const error = document.querySelector("#shift-close-error");
    const button = document.querySelector("#submit-shift-close");
    if (field.value === "" || !field.reportValidity()) return;
    button.disabled = true;
    error.classList.add("hidden");
    try {
        const closedShift = await api("/api/v1/turnos/cierre", {
            method: "POST",
            body: JSON.stringify({
                efectivoReal: Number(field.value),
                observaciones: document.querySelector("#shift-close-observations").value.trim() || null
            })
        });
        currentShift = null;
        renderShiftIndicator();
        openShiftAudit(closedShift);
        showToast(`Turno cerrado. Variación: ${money(closedShift.variacion)}.`);
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
});
document.querySelector("#print-shift-report").addEventListener("click", () => {
    document.body.classList.add("shift-print");
    window.print();
});
document.querySelector("#finish-shift-report").addEventListener("click", () => document.querySelector("#shift-dialog").close());
window.addEventListener("afterprint", () => document.body.classList.remove("shift-print"));
window.addEventListener("afterprint", () => document.body.classList.remove("thermal-ticket-print"));
document.querySelector('[data-module="pos"]').addEventListener("click", event => {
    event.preventDefault();
    showPointOfSale();
});
document.querySelector("#new-product-button").addEventListener("click", () => openProductForm());
const bulkProductsDialog = document.querySelector("#bulk-products-dialog");
const bulkProductsForm = document.querySelector("#bulk-products-form");
const bulkProductsFile = document.querySelector("#bulk-products-file");
const bulkProductsError = document.querySelector("#bulk-products-error");
const bulkProductsText = document.querySelector("#bulk-products-text");
const bulkProductsTextFile = document.querySelector("#bulk-products-text-file");
const bulkProductsTextPreview = document.querySelector("#bulk-products-text-preview");
const bulkProductsTextButton = document.querySelector("#submit-bulk-products-text");
const bulkProductsDropzone = document.querySelector("#bulk-products-dropzone");
function actualizarVistaPreviaTexto() {
    const filas = bulkProductsText.value.split(/\r?\n/).filter(fila => fila.trim());
    bulkProductsTextPreview.textContent = `Vista previa: ${filas.length} fila(s) listas para importar.`;
}
bulkProductsText.addEventListener("input", actualizarVistaPreviaTexto);
bulkProductsTextFile.addEventListener("change", async () => {
    const archivo = bulkProductsTextFile.files[0];
    if (!archivo) return;
    bulkProductsText.value = await archivo.text();
    actualizarVistaPreviaTexto();
});
["dragenter", "dragover"].forEach(eventName => bulkProductsDropzone.addEventListener(eventName, event => {
    event.preventDefault();
    bulkProductsDropzone.classList.add("is-dragging");
}));
["dragleave", "drop"].forEach(eventName => bulkProductsDropzone.addEventListener(eventName, event => {
    event.preventDefault();
    bulkProductsDropzone.classList.remove("is-dragging");
}));
bulkProductsDropzone.addEventListener("drop", event => {
    const archivo = event.dataTransfer.files[0];
    if (!archivo) return;
    bulkProductsTextFile.files = event.dataTransfer.files;
    archivo.text().then(contenido => {
        bulkProductsText.value = contenido;
        actualizarVistaPreviaTexto();
    });
});
document.querySelector("#bulk-products-button").addEventListener("click", () => {
    bulkProductsForm.reset();
    bulkProductsText.value = "";
    actualizarVistaPreviaTexto();
    bulkProductsError.classList.add("hidden");
    bulkProductsDialog.showModal();
});
bulkProductsTextButton.addEventListener("click", async () => {
    if (!bulkProductsText.value.trim()) {
        bulkProductsError.textContent = "Pega filas o selecciona un archivo TXT/CSV.";
        bulkProductsError.classList.remove("hidden");
        return;
    }
    bulkProductsTextButton.disabled = true;
    try {
        const resultado = await api("/api/v1/productos/carga-masiva-texto", {
            method: "POST",
            headers: {"Content-Type": "text/plain;charset=UTF-8"},
            body: bulkProductsText.value
        });
        await loadAdminProducts();
        bulkProductsDialog.close();
        showToast(`${resultado.insercionesExitosas} de ${resultado.totalProcesados} productos cargados.`);
    } catch (error) {
        bulkProductsError.textContent = error.message;
        bulkProductsError.classList.remove("hidden");
    } finally {
        bulkProductsTextButton.disabled = false;
    }
});
document.querySelector("#close-bulk-products-dialog").addEventListener("click", () => bulkProductsDialog.close());
document.querySelector("#cancel-bulk-products").addEventListener("click", () => bulkProductsDialog.close());
bulkProductsForm.addEventListener("submit", async event => {
    event.preventDefault();
    const archivo = bulkProductsFile.files[0];
    if (!archivo) return;
    const button = document.querySelector("#submit-bulk-products");
    button.disabled = true;
    bulkProductsError.classList.add("hidden");
    try {
        const resultado = await api("/api/v1/productos/carga-masiva", {
            method: "POST",
            body: (() => {
                const formData = new FormData();
                formData.append("archivo", archivo);
                return formData;
            })()
        });
        await loadAdminProducts();
        bulkProductsDialog.close();
        const detalleErrores = resultado.errores?.length
            ? ` Errores: ${resultado.errores.join(" ")}`
            : "";
        showToast(`${resultado.insercionesExitosas} de ${resultado.totalProcesados} productos cargados.${detalleErrores}`);
    } catch (error) {
        bulkProductsError.textContent = error.message;
        bulkProductsError.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
});
document.querySelector("#new-customer-button").addEventListener("click", () => openCustomerForm());
document.querySelector("#close-customer-dialog").addEventListener("click", () => document.querySelector("#customer-dialog").close());
document.querySelector("#cancel-customer-form").addEventListener("click", () => document.querySelector("#customer-dialog").close());
document.querySelector("#customer-search").addEventListener("input", () => {
    window.clearTimeout(customerSearchTimer);
    customerSearchTimer = window.setTimeout(loadClientes, 200);
});
const customerGiroInput = document.querySelector("#customer-giro");
customerGiroInput.addEventListener("input", () => {
    document.querySelector("#customer-activity-code").value = "";
    document.querySelector("#activity-selection").textContent = "";
    window.clearTimeout(activitySearchTimer);
    activitySearchTimer = window.setTimeout(buscarActividadesEconomicas, 180);
});
customerGiroInput.addEventListener("focus", () => {
    if (customerGiroInput.value.trim()) buscarActividadesEconomicas();
});
customerGiroInput.addEventListener("keydown", event => {
    if (event.key === "Escape") {
        document.querySelector("#activity-suggestions").classList.add("hidden");
        customerGiroInput.setAttribute("aria-expanded", "false");
    }
});
document.querySelector("#activity-suggestions").addEventListener("click", event => {
    const option = event.target.closest("[data-activity-code]");
    if (!option) return;
    customerGiroInput.value = option.dataset.activityDescription;
    document.querySelector("#customer-activity-code").value = option.dataset.activityCode;
    document.querySelector("#activity-selection").textContent =
        `Actividad seleccionada · Código ${option.dataset.activityCode}`;
    document.querySelector("#activity-suggestions").classList.add("hidden");
    customerGiroInput.setAttribute("aria-expanded", "false");
});
document.querySelector("#customers-table-body").addEventListener("click", async event => {
    const button = event.target.closest("[data-customer-edit], [data-customer-disable]");
    if (!button) return;
    const id = Number(button.dataset.customerEdit ?? button.dataset.customerDisable);
    const cliente = clientes.find(item => item.id === id);
    if (!cliente) return;
    if (button.hasAttribute("data-customer-edit")) {
        openCustomerForm(cliente);
        return;
    }
    if (!window.confirm(`¿Deseas deshabilitar a "${cliente.nombre}"?`)) return;
    try {
        await api(`/api/v1/clientes/${id}`, { method: "DELETE" });
        showToast("Cliente deshabilitado.");
        await loadClientes();
    } catch (error) {
        showToast(error.message);
    }
});
document.querySelector("#customer-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const request = Object.fromEntries(
        ["nombre", "tipoDocumento", "documento", "nrc", "giro", "codigoActividad", "telefono", "email", "direccion"]
            .map(key => [key, String(formData.get(key) ?? "").trim() || null]));
    const errorElement = document.querySelector("#customer-form-error");
    const button = document.querySelector("#save-customer-button");
    const wasEditing = Boolean(editingCustomer);
    button.disabled = true;
    errorElement.classList.add("hidden");
    try {
        const saved = await api(editingCustomer ? `/api/v1/clientes/${editingCustomer.id}` : "/api/v1/clientes", {
            method: editingCustomer ? "PUT" : "POST",
            body: JSON.stringify(request)
        });
        if (selectedCheckoutCustomer?.id === saved.id || (elements.dialog.open && !wasEditing)) {
            selectedCheckoutCustomer = saved;
            document.querySelector("#checkout-customer-name").textContent =
                `${saved.nombre}${saved.documento ? ` · ${saved.documento}` : ""}`;
            actualizarRequisitosComprobante();
        }
        document.querySelector("#customer-dialog").close();
        showToast(editingCustomer ? "Cliente actualizado." : "Cliente creado.");
        await loadClientes();
    } catch (error) {
        errorElement.textContent = error.message;
        errorElement.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
});
document.querySelector("#new-supplier-button").addEventListener("click", () => {
    supplierQuickCreate = false;
    openSupplierForm();
});
document.querySelector("#purchase-new-supplier").addEventListener("click", () => {
    supplierQuickCreate = true;
    openSupplierForm();
});
document.querySelector("#close-supplier-dialog").addEventListener("click", () => document.querySelector("#supplier-dialog").close());
document.querySelector("#cancel-supplier-form").addEventListener("click", () => document.querySelector("#supplier-dialog").close());
document.querySelector("#supplier-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#supplier-form-error");
    const wasEditing = Boolean(editingSupplier);
    const quickCreate = supplierQuickCreate && !wasEditing;
    const request = Object.fromEntries(
        ["nombreComercial", "razonSocial", "nrc", "nit", "telefono", "email", "direccion"]
            .map(key => [key, String(new FormData(form).get(key) ?? "").trim() || null]));
    error.classList.add("hidden");
    let saved;
    try {
        saved = await api(wasEditing ? `/api/v1/proveedores/${editingSupplier.id}` : "/api/v1/proveedores", {
            method: wasEditing ? "PUT" : "POST",
            body: JSON.stringify(request)
        });
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
        return;
    }
    document.querySelector("#supplier-dialog").close();
    editingSupplier = null;
    supplierQuickCreate = false;
    showToast(wasEditing ? "Proveedor actualizado." : "Proveedor guardado.");
    try {
        if (quickCreate) {
            await loadPurchaseFormData();
            document.querySelector("#purchase-supplier").value = String(saved.id);
        }
        if (!elements.purchasesAdmin.classList.contains("hidden")
                && document.querySelector("#purchase-suppliers-panel").classList.contains("hidden") === false) {
            await loadProveedores();
        }
    } catch (refreshError) {
        showToast(`El proveedor se guardó, pero no se pudo actualizar la lista: ${refreshError.message}`);
    }
});
document.querySelector("#suppliers-table-body").addEventListener("click", async event => {
    const button = event.target.closest("[data-supplier-edit], [data-supplier-disable]");
    if (!button) return;
    const id = Number(button.dataset.supplierEdit ?? button.dataset.supplierDisable);
    const supplier = proveedores.find(item => item.id === id);
    if (!supplier) return;
    if (button.hasAttribute("data-supplier-edit")) {
        supplierQuickCreate = false;
        openSupplierForm(supplier);
        return;
    }
    if (!window.confirm(`¿Deseas deshabilitar a "${supplier.nombreComercial}"?`)) return;
    try {
        await api(`/api/v1/proveedores/${id}`, { method: "DELETE" });
        showToast("Proveedor deshabilitado.");
        await loadProveedores();
    } catch (error) {
        showToast(error.message);
    }
});
document.querySelector("#purchase-add-line").addEventListener("click", addPurchaseLine);
document.querySelector("#purchase-new-product").addEventListener("click", () => {
    purchaseQuickCreate = true;
    activePurchaseLine = addPurchaseLine();
    openProductForm();
});
document.querySelector("#purchase-lines").addEventListener("input", renderPurchaseTotal);
document.querySelector("#purchase-lines").addEventListener("change", renderPurchaseTotal);
document.querySelector("#purchase-lines").addEventListener("change", event => {
    if (!event.target.matches(".purchase-line-product") || event.target.value !== "__new__") return;
    activePurchaseLine = event.target.closest(".purchase-line");
    event.target.value = "";
    purchaseQuickCreate = true;
    openProductForm();
});
document.querySelector("#purchase-lines").addEventListener("click", event => {
    if (!event.target.closest("[data-purchase-remove]")) return;
    event.target.closest(".purchase-line").remove();
    if (!document.querySelector("#purchase-lines").children.length) addPurchaseLine();
    renderPurchaseTotal();
});
document.querySelector("#purchase-submit").addEventListener("click", async event => {
    const button = event.currentTarget;
    const error = document.querySelector("#purchase-form-error");
    const supplierId = Number(document.querySelector("#purchase-supplier").value);
    const rows = [...document.querySelectorAll(".purchase-line")];
    const items = rows.map(row => ({
        productoId: Number(row.querySelector(".purchase-line-product").value),
        cantidad: Number(row.querySelector(".purchase-line-quantity").value),
        costoUnitario: Number(row.querySelector(".purchase-line-cost").value)
    }));
    if (!supplierId || items.some(item => !item.productoId || !Number.isInteger(item.cantidad) || item.cantidad < 1
            || !Number.isFinite(item.costoUnitario) || item.costoUnitario < 0)) {
        error.textContent = "Selecciona un proveedor y completa cada producto, cantidad y costo unitario.";
        error.classList.remove("hidden");
        return;
    }
    error.classList.add("hidden");
    button.disabled = true;
    try {
        const purchase = await api("/api/v1/compras", {
            method: "POST",
            body: JSON.stringify({
                proveedorId: supplierId,
                numeroFacturaProveedor: document.querySelector("#purchase-invoice-number").value.trim() || null,
                items
            })
        });
        document.querySelector("#purchase-invoice-number").value = "";
        document.querySelector("#purchase-lines").replaceChildren();
        showToast(`Compra ${purchase.folioInterno} registrada por ${money(purchase.total)}.`);
        try {
            await loadPurchaseFormData();
        } catch (refreshError) {
            addPurchaseLine();
            showToast(`La compra se registró, pero no se pudo actualizar el formulario: ${refreshError.message}`);
        }
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
});
document.querySelector("#purchases-table-body").addEventListener("click", event => {
    const button = event.target.closest("[data-purchase-detail]");
    if (!button) return;
    const purchase = purchaseHistory.find(item => item.id === Number(button.dataset.purchaseDetail));
    if (purchase) mostrarDetalleCompra(purchase);
});
document.querySelector("#close-purchase-detail").addEventListener("click", () => document.querySelector("#purchase-detail-dialog").close());
document.querySelector("#close-purchase-detail-button").addEventListener("click", () => document.querySelector("#purchase-detail-dialog").close());
document.querySelector("#cancel-product-form").addEventListener("click", () => {
    purchaseQuickCreate = false;
    activePurchaseLine = null;
    elements.productDialog.close();
});
elements.productDialog.addEventListener("cancel", () => {
    purchaseQuickCreate = false;
    activePurchaseLine = null;
});
document.querySelector("#close-product-dialog").addEventListener("click", () => {
    purchaseQuickCreate = false;
    activePurchaseLine = null;
    elements.productDialog.close();
});
elements.adminSearch.addEventListener("input", renderAdminProducts);
elements.adminCategory.addEventListener("change", renderAdminProducts);
document.querySelector("#close-inventory-adjust").addEventListener("click", () => elements.inventoryAdjustDialog.close());
document.querySelector("#cancel-inventory-adjust").addEventListener("click", () => elements.inventoryAdjustDialog.close());
document.querySelector("#close-ticket-dialog").addEventListener("click", () => elements.historyDialog.close());
document.querySelector("#print-ticket-button").addEventListener("click", () => openTicketModal());
document.querySelector("#cancel-sale-button").addEventListener("click", async () => {
    if (!currentSale || !isAdmin || currentSale.estado !== "COMPLETADA") return;
    const motivo = window.prompt("Motivo de anulación:");
    if (motivo === null || !motivo.trim()) return;
    try {
        await api(`/api/v1/ventas/${currentSale.id}/anulacion`, {
            method: "POST",
            body: JSON.stringify({ motivo: motivo.trim() })
        });
        elements.historyDialog.close();
        await loadSalesPage();
        showToast("Venta anulada. Se restauró el inventario y se registró la reversión.");
    } catch (error) {
        showToast(error.message);
    }
});
document.querySelector("#sign-dte-button").addEventListener("click", async event => {
    if (!currentSale) return;
    const button = event.currentTarget;
    const result = document.querySelector("#signed-dte-result");
    button.disabled = true;
    result.classList.add("hidden");
    try {
        const documentDte = await api(`/api/v1/dte/ventas/${currentSale.id}/firmar`, { method: "POST" });
        let parsedDte = null;
        try { parsedDte = JSON.parse(documentDte.documentoFirmado); } catch (error) { parsedDte = null; }
        openTicketModal(currentSale, {
            ...documentDte,
            numeroControl: parsedDte?.identificacion?.numeroControl,
            selloRecepcion: parsedDte?.selloRecibido ?? parsedDte?.selloRecepcion
        });
        result.textContent = documentDte.documentoFirmado;
        result.classList.remove("hidden");
        showToast(`DTE ${documentDte.tipoDte} firmado correctamente.`);
    } catch (error) {
        showToast(error.message);
    } finally {
        button.disabled = false;
    }
});
document.querySelector("#sales-search-button").addEventListener("click", () => {
    salesPage = 0;
    loadSalesPage();
});
document.querySelector("#sales-previous-page").addEventListener("click", () => {
    if (salesPage > 0) {
        salesPage--;
        loadSalesPage();
    }
});
document.querySelector("#sales-next-page").addEventListener("click", () => {
    if (salesPage + 1 < salesPageCount) {
        salesPage++;
        loadSalesPage();
    }
});
elements.salesTableBody.addEventListener("click", event => {
    const button = event.target.closest("[data-sale-detail]");
    if (button) openSaleDetails(Number(button.dataset.saleDetail));
});
document.querySelector("#process-return-button").addEventListener("click", () => {
    if (!canProcessReturns || !currentSale) return;
    pendingReturnItems = currentSale.detalles.map(item => ({
        productoId: item.productoId,
        cantidad: item.cantidad
    }));
    const amount = calcularImporteDevolucionEstimado(currentSale, pendingReturnItems);
    const detailById = new Map(currentSale.detalles.map(item => [item.productoId, item]));
    document.querySelector("#return-confirmation-folio").textContent =
        `Venta ${currentSale.folioInterno ?? currentSale.numeroOrden}`;
    document.querySelector("#return-confirmation-items").innerHTML = pendingReturnItems.map(item => {
        const detail = detailById.get(item.productoId);
        return `<div><span>${item.cantidad} × ${escapeHtml(detail.nombreProducto)}</span><strong>${money(cents(detail.precioUnitario) * item.cantidad / 100)}</strong></div>`;
    }).join("");
    document.querySelector("#return-confirmation-total").textContent =
        `${money(amount)} · Se devolverá el ticket completo; no hay devoluciones parciales.`;
    document.querySelector("#return-confirmation-error").classList.add("hidden");
    const form = document.querySelector("#return-confirmation-form");
    form.elements.namedItem("motivo").value = "";
    document.querySelector("#return-confirmation-dialog").showModal();
});
document.querySelector("#return-confirmation-form").addEventListener("submit", async event => {
    event.preventDefault();
    if (!canProcessReturns || !currentSale || pendingReturnItems.length === 0) return;
    const form = event.currentTarget;
    const button = document.querySelector("#confirm-return-button");
    const error = document.querySelector("#return-confirmation-error");
    error.classList.add("hidden");
    button.disabled = true;
    try {
        const saleId = currentSale.id;
        const result = await api(`/api/v1/ventas/${saleId}/devolucion`, {
            method: "POST",
            body: JSON.stringify({
                motivo: form.elements.namedItem("motivo").value.trim()
            })
        });
        document.querySelector("#return-confirmation-dialog").close();
        elements.historyDialog.close();
        pendingExchangeFolio = currentSale.folioInterno;
        cart.clear();
        for (const item of currentSale.detalles) {
            const product = products.find(candidate => candidate.id === item.productoId);
            if (product) cart.set(product.id, { product, quantity: item.cantidad });
        }
        renderProducts();
        renderCart();
        await Promise.all([loadSalesPage(), openSaleDetails(saleId)]);
        showToast(`Cambio iniciado para ${currentSale.folioInterno}. Agrega o ajusta productos y cobra una venta por un monto igual o mayor a ${money(result.importe)}.`);
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
});
document.querySelector("#close-return-confirmation").addEventListener("click", () => {
    document.querySelector("#return-confirmation-dialog").close();
});
document.querySelector("#cancel-return-confirmation").addEventListener("click", () => {
    document.querySelector("#return-confirmation-dialog").close();
});
elements.inventoryProductsBody.addEventListener("click", event => {
    const button = event.target.closest("[data-inventory-adjust]");
    if (!button) return;
    elements.inventoryAdjustForm.reset();
    elements.inventoryProductSelect.value = button.dataset.inventoryAdjust;
    elements.inventoryAdjustError.classList.add("hidden");
    elements.inventoryAdjustDialog.showModal();
});
elements.inventoryAdjustForm.addEventListener("submit", async event => {
    event.preventDefault();
    const formData = new FormData(elements.inventoryAdjustForm);
    const saveButton = document.querySelector("#save-inventory-adjust");
    saveButton.disabled = true;
    elements.inventoryAdjustError.classList.add("hidden");
    try {
        await api("/api/v1/inventario/ajuste", {
            method: "POST",
            body: JSON.stringify({
                productoId: Number(formData.get("productoId")),
                cantidad: Number(formData.get("cantidad")),
                tipo: formData.get("tipo"),
                tipoOperacion: "AI",
                motivo: String(formData.get("motivo")).trim()
            })
        });
        elements.inventoryAdjustDialog.close();
        showToast("Movimiento registrado correctamente.");
        await loadInventory();
    } catch (error) {
        elements.inventoryAdjustError.textContent = error.message;
        elements.inventoryAdjustError.classList.remove("hidden");
    } finally {
        saveButton.disabled = false;
    }
});

elements.adminProductsBody.addEventListener("click", async event => {
    const button = event.target.closest("[data-product-action]");
    if (!button) return;
    const id = Number(button.dataset.productId);
    const product = adminProducts.find(item => item.id === id);
    if (!product) return;

    if (button.dataset.productAction === "edit") {
        try {
            openProductForm(await api(`/api/v1/productos/${id}`));
        } catch (error) {
            showToast(error.message);
        }
        return;
    }

    const action = product.activo ? "desactivar" : "quitar del catálogo";
    if (!window.confirm(`¿Deseas ${action} "${product.nombre}"? Se conservarán sus ventas anteriores.`)) return;
    try {
        await api(`/api/v1/productos/${id}`, { method: "DELETE" });
        showToast("Producto desactivado; sus ventas anteriores se conservaron.");
        await loadAdminProducts();
    } catch (error) {
        showToast(error.message);
    }
});

elements.productForm.addEventListener("submit", async event => {
    event.preventDefault();
    const formData = new FormData(elements.productForm);
    const request = {
        nombre: String(formData.get("nombre")).trim(),
        categoria: String(formData.get("categoria")).trim(),
        precio: Number(formData.get("precio")),
        precioIncluyeIva: elements.productForm.elements.precioIncluyeIva.checked,
        stock: purchaseQuickCreate && !editingProduct ? 0 : Number(formData.get("stock")),
        favorito: editingProduct?.favorito ?? false,
        activo: purchaseQuickCreate && !editingProduct ? true : elements.productForm.elements.activo.checked,
        codigoBarras: String(formData.get("codigoBarras") ?? "").trim() || null
    };
    const crearDesdeCompra = purchaseQuickCreate && !editingProduct;
    const saveButton = document.querySelector("#save-product-button");
    saveButton.disabled = true;
    elements.productFormError.classList.add("hidden");
    try {
        const path = editingProduct
            ? `/api/v1/productos/${editingProduct.id}`
            : "/api/v1/productos";
        const saved = await api(path, {
            method: editingProduct ? "PUT" : "POST",
            body: JSON.stringify(request)
        });
        const wasEditing = Boolean(editingProduct);
        editingProduct = saved;
        elements.productDialog.close();
        showToast(wasEditing ? "Producto actualizado." : "Producto creado.");
    } catch (error) {
        elements.productFormError.textContent = error.message;
        elements.productFormError.classList.remove("hidden");
        return;
    } finally {
        saveButton.disabled = false;
    }
    try {
        if (crearDesdeCompra) {
            purchaseProducts = (await api("/api/v1/productos")).filter(product => product.activo);
            document.querySelectorAll(".purchase-line-product").forEach(select => {
                select.innerHTML = purchaseProductOptions();
            });
            if (activePurchaseLine?.isConnected) {
                activePurchaseLine.querySelector(".purchase-line-product").value = String(editingProduct.id);
                activePurchaseLine.querySelector(".purchase-line-cost").focus();
            }
            purchaseQuickCreate = false;
            activePurchaseLine = null;
        } else {
            await loadAdminProducts();
        }
    } catch (error) {
        purchaseQuickCreate = false;
        activePurchaseLine = null;
        showToast(`Se guardó el producto, pero no se pudo actualizar la lista: ${error.message}`);
    }
});

document.querySelector("#discount-button").addEventListener("click", () => {
    if (cart.size === 0) {
        showToast("Agrega un producto antes de aplicar el descuento.");
        return;
    }
    if (totalDescuentoManual() > 0) {
        limpiarDescuentosManuales();
        renderCart();
        updatePaymentChange();
        return;
    }
    abrirAutorizacionDescuento("ticket");
});
document.querySelector("#close-discount-authorization").addEventListener("click", () =>
    document.querySelector("#discount-authorization-dialog").close());
document.querySelector("#cancel-discount-authorization").addEventListener("click", () =>
    document.querySelector("#discount-authorization-dialog").close());
document.querySelector("#discount-authorization-dialog").addEventListener("close", () => {
    document.querySelector("#discount-authorization-form").elements.password.value = "";
});
document.querySelector("#discount-authorization-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.querySelector("#discount-authorization-error");
    const amount = Number(form.elements.amount.value);
    const previousAmount = pendingDiscountTarget === "ticket"
        ? manualDiscountAmount
        : Number(cart.get(pendingDiscountProductId)?.manualDiscountAmount ?? 0);
    const otherDiscounts = totalDescuentoManual() - previousAmount;
    const amountToAuthorize = otherDiscounts + amount;
    const line = pendingDiscountProductId == null ? null : cart.get(pendingDiscountProductId);
    const lineSubtotal = line
        ? precioBaseUnitarioCentavos(line.product) * line.quantity / 100
        : Number.POSITIVE_INFINITY;
    if (!Number.isFinite(amount) || amount <= 0
        || (pendingDiscountTarget === "line" && !line)
        || amountToAuthorize > totals().subtotal - automaticDiscountAmount
        || (pendingDiscountTarget === "line" && amount > lineSubtotal)) {
        error.textContent = "El descuento debe ser positivo y no superar el saldo de la venta.";
        error.classList.remove("hidden");
        return;
    }
    try {
        const authorization = await api("/api/v1/auth/discount-authorization", {
            method: "POST",
            body: JSON.stringify({
                amount: amountToAuthorize.toFixed(2),
                username: form.elements.username.value,
                password: form.elements.password.value
            })
        });
        if (pendingDiscountTarget === "ticket") {
            manualDiscountAmount = amount;
        } else if (line) {
            line.manualDiscountAmount = amount;
        }
        discountAuthorizationToken = authorization.token;
        form.elements.password.value = "";
        document.querySelector("#discount-authorization-dialog").close();
        renderCart();
        updatePaymentChange();
        showToast(`Descuento de ${money(amount)} autorizado.`);
    } catch (authorizationError) {
        form.elements.password.value = "";
        error.textContent = authorizationError.message;
        error.classList.remove("hidden");
    }
});

document.querySelectorAll("[data-nav-message]").forEach(link => link.addEventListener("click", event => {
    event.preventDefault();
    showToast(link.dataset.navMessage);
}));

document.querySelector("#logout-link").addEventListener("click", async event => {
    event.preventDefault();
    try {
        const csrf = await api("/api/v1/auth/csrf");
        const response = await fetch("/logout", {
            method: "POST",
            credentials: "same-origin",
            headers: { [csrf.headerName]: csrf.token }
        });
        if (!response.ok) {
            throw new Error(`No se pudo cerrar la sesión (${response.status}).`);
        }
        window.location.assign("/login?logout");
    } catch (error) {
        showToast(error.message);
    }
});

document.addEventListener("keydown", event => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        elements.search.focus();
    }
});

elements.checkoutButton.addEventListener("click", async () => {
    if (!currentShift) {
        openShiftOpeningDialog();
        return;
    }
    if (!await actualizarCotizacionPromocion()) {
        showToast("No se pudo consultar la promoción. Intenta de nuevo.");
        return;
    }
    const totalVenta = totals().total;
    totalCheckout = totalVenta;
    montoRestante = totalVenta;
    pagosParciales = [];
    elements.dialogTotal.textContent = money(totalVenta);
    resetPartialPaymentFields();
    renderPartialPayments();
    selectedCheckoutCustomer = null;
    ventaExenta = false;
    document.querySelector("#chk-venta-exenta").checked = false;
    receiptType = "CONSUMIDOR_FINAL";
    document.querySelectorAll(".receipt-type-button").forEach(option => {
        const selected = option.dataset.receiptType === receiptType;
        option.classList.toggle("active", selected);
        option.setAttribute("aria-pressed", String(selected));
    });
    document.querySelector("#clear-checkout-customer").classList.remove("hidden");
    document.querySelector("#checkout-customer-search").value = "";
    document.querySelector("#checkout-customer-name").textContent = "Cliente General / Consumidor Final";
    document.querySelector("#checkout-customer-suggestions").classList.add("hidden");
    document.querySelector("#input-card-last4").value = "";
    document.querySelector("#input-auth-code").value = "";
    document.querySelector("#input-return-folio").value = pendingExchangeFolio ?? "";
    actualizarRequisitosComprobante();
    elements.error.classList.add("hidden");
    elements.changeLine.classList.add("hidden");
    elements.dialog.showModal();
    if (paymentMethod === "Efectivo") elements.cashInput.focus();
});

const checkoutCustomerSearch = document.querySelector("#checkout-customer-search");
checkoutCustomerSearch.addEventListener("focus", buscarClientesParaCheckout);
checkoutCustomerSearch.addEventListener("input", () => {
    selectedCheckoutCustomer = null;
    document.querySelector("#checkout-customer-name").textContent = "Cliente General / Consumidor Final";
    actualizarRequisitosComprobante();
    buscarClientesParaCheckout();
});
document.querySelector("#checkout-customer-suggestions").addEventListener("click", event => {
    const option = event.target.closest("[data-checkout-customer]");
    if (!option) return;
    selectedCheckoutCustomer = checkoutClientes.find(cliente => cliente.id === Number(option.dataset.checkoutCustomer)) ?? null;
    if (!selectedCheckoutCustomer) return;
    document.querySelector("#checkout-customer-name").textContent =
        `${selectedCheckoutCustomer.nombre}${selectedCheckoutCustomer.documento ? ` · ${selectedCheckoutCustomer.documento}` : ""}`;
    checkoutCustomerSearch.value = "";
    document.querySelector("#checkout-customer-suggestions").classList.add("hidden");
    actualizarRequisitosComprobante();
});
document.querySelector("#clear-checkout-customer").addEventListener("click", () => {
    selectedCheckoutCustomer = null;
    checkoutCustomerSearch.value = "";
    document.querySelector("#checkout-customer-name").textContent = "Cliente General / Consumidor Final";
    document.querySelector("#checkout-customer-suggestions").classList.add("hidden");
    actualizarRequisitosComprobante();
});

document.querySelector(".receipt-type-selector").addEventListener("click", event => {
    const button = event.target.closest("[data-receipt-type]");
    if (!button) return;
    receiptType = button.dataset.receiptType;
    document.querySelectorAll(".receipt-type-button").forEach(option => {
        const selected = option === button;
        option.classList.toggle("active", selected);
        option.setAttribute("aria-pressed", String(selected));
    });
    document.querySelector("#clear-checkout-customer").classList.toggle("hidden", receiptType === "CREDITO_FISCAL");
    actualizarRequisitosComprobante();
});
document.querySelector("#complete-fiscal-customer").addEventListener("click", () => {
    if (selectedCheckoutCustomer) openCustomerForm(selectedCheckoutCustomer);
});
document.querySelector("#checkout-new-customer").addEventListener("click", () => openCustomerForm());

document.querySelector(".payment-methods").addEventListener("click", event => {
    const button = event.target.closest("[data-payment]");
    if (!button) return;
    paymentMethod = button.dataset.payment;
    document.querySelectorAll(".payment-method").forEach(method => {
        const selected = method === button;
        method.classList.toggle("active", selected);
        method.setAttribute("aria-pressed", String(selected));
    });
    document.querySelector("#chk-venta-exenta").addEventListener("change", event => {
        ventaExenta = event.currentTarget.checked;
        const current = totals();
        elements.dialogTotal.textContent = money(current.total);
        renderCart();
        updatePaymentChange();
    });
    elements.cashField.classList.remove("hidden");
    document.querySelector("#cash-payment-card").classList.remove("hidden");
    document.querySelector("#return-folio-field").classList.toggle("hidden", paymentMethod !== "Devolucion");
    document.querySelector("#payment-terminal-note").classList.toggle("hidden",
        !["Cuscatlan", "Nico", "Credomatic", "Mensajeria", "Tarjeta"].includes(paymentMethod));
    const cardSection = document.querySelector("#card-voucher-container");
    const method = button.getAttribute("data-method") || button.textContent.trim();
    const isCardPayment = ["cuscatlan", "credomatic", "nico"].some(cardMethod =>
        method.toLowerCase().includes(cardMethod));
    if (cardSection) {
        cardSection.classList.toggle("hidden", !isCardPayment);
        cardSection.style.display = isCardPayment ? "block" : "none";
    }
    elements.cashInput.required = true;
    updatePaymentChange();
});

elements.cashInput.addEventListener("input", updatePaymentChange);

function validarFormatoFolio(folio, tipo) {
    if (!folio) return false;
    const regexFolio = /^S\d{2}C\d{2}[A-Z]{2}\d{6}$/;
    return tipo === "DE"
        ? folio.startsWith("DE") || regexFolio.test(folio)
        : regexFolio.test(folio);
}

async function confirmarPago(event) {
    event?.preventDefault();
    const current = totals();
    if (pagosParciales.length === 0) {
        agregarPagoParcial();
    }
    if (montoRestante > 0.001 || pagosParciales.length === 0) {
        if (pagosParciales.length > 0) {
            elements.error.textContent = "Agrega pagos hasta cubrir el total de la venta.";
        }
        elements.error.classList.remove("hidden");
        return;
    }
    const totalReceived = pagosParciales
        .reduce((sum, pago) => sum + Number(pago.monto || 0), 0);
    const metodoPagoFinal = pagosParciales.length > 1 ? "Mixto" : pagosParciales[0].metodoPago;
    const esDevolucion = pagosParciales.some(pago =>
        pago.metodoPago === "Devolucion" || pago.metodoPago === "Devolución");
    const folioDevolucion = esDevolucion
        ? pagosParciales.find(pago =>
            pago.metodoPago === "Devolucion" || pago.metodoPago === "Devolución")?.folioDevolucion ?? null
        : null;

    const button = document.querySelector("#confirm-payment");
    button.disabled = true;
    elements.error.classList.add("hidden");
    try {
        const sale = await api("/api/v1/pos/cobrar", {
            method: "POST",
            body: JSON.stringify({
                metodoPago: metodoPagoFinal,
                montoPagado: Number.isFinite(totalReceived) ? totalReceived : 0,
                pagosParciales,
                esExenta: ventaExenta,
                ultimos4Digitos: null,
                codigoAutorizacion: null,
                folioDevolucion,
                items: [...cart.values()].map(({ product, quantity, manualDiscountAmount: lineDiscount = 0 }) => ({
                    productoId: product.id,
                    cantidad: quantity,
                    descuentoManual: lineDiscount
                })),
                descuento: manualDiscountAmount,
                descuentoAuthorizationToken: discountAuthorizationToken,
                clienteId: selectedCheckoutCustomer?.id ?? null,
                tipoComprobante: receiptType,
                folioVentaOrigen: esDevolucion ? folioDevolucion : pendingExchangeFolio
            })
        });
        const saleResponse = {
            ...sale,
            efectivoRecibido: totalReceived,
            totalPagado: totalReceived,
            cambio: Math.max(0, Number((totalReceived - Number(sale.total)).toFixed(2)))
        };
        openAndPrintSaleTicket(saleResponse);
        await loadCurrentShift(false);
        const change = saleResponse.cambio;
        for (const { product, quantity } of cart.values()) {
            product.stock -= quantity;
        }
        elements.dialog.close();
        cart.clear();
        manualDiscountAmount = 0;
        discountAuthorizationToken = null;
        automaticDiscountAmount = 0;
        automaticPromotionName = null;
        promotionQuoteSequence++;
        elements.orderNumber.textContent = sale.numeroOrden;
        renderProducts();
        renderCart();
        showToast(`Venta ${sale.numeroOrden} registrada (${sale.folioInterno}). Total: ${money(sale.total)}.${change > 0 ? ` Cambio: ${money(change)}.` : ""}`);
    } catch (error) {
        elements.error.textContent = error.message;
        elements.error.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
}

document.querySelector("#confirm-payment").addEventListener("click", confirmarPago);
elements.addPartialPayment.addEventListener("click", agregarPagoParcial);
