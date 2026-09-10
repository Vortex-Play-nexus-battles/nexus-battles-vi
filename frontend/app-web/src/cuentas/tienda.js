// --- CONFIGURACIÓN ---
const API_BASE_URL = 'http://localhost:8090/api/v1/ecommerce';
const USER_ID = 'usr_test_123'; // Simulación del ID que provee ms-identidad

// Configuración genérica para los headers requeridos
const getHeaders = () => ({
    'Content-Type': 'application/json',
    'X-User-Id': USER_ID
});

// --- INICIALIZACIÓN ---
document.addEventListener('DOMContentLoaded', () => {
    cargarVitrina();
    cargarCarrito();
});

// --- RENDERIZADO DE PRODUCTOS (HU-CAR-001) ---
async function cargarVitrina() {
    try {
        // Usa el interceptor de tu equipo
        const response = await fetchWithHttpErrorInterceptor(`${API_BASE_URL}/productos`, {
            method: 'GET',
            headers: getHeaders()
        });

        const data = await response.json();
        const productosGrid = document.getElementById('productos-grid');
        productosGrid.innerHTML = ''; // Limpiar loader

        // Iteramos sobre la lista de productos (data.content según el JSON de Spring)
        data.content.forEach(producto => {
            // Se asignan colores dinámicos a la imagen según el tipo para simular el Figma
            const colorBox = producto.tipo === 'ARMA' ? '#006b8f' : '#6a1b9a';

            const card = document.createElement('div');
            card.className = 'product-card';
            card.innerHTML = `
                <div class="product-image" style="background-color: ${colorBox};"></div>
                <h4>${producto.nombre}</h4>
                <p>${producto.descripcion}</p>
                <div class="product-footer">
                    <span class="product-price">${producto.precioFinal} ${producto.moneda}</span>
                    <button class="btn-anadir" onclick="agregarAlCarrito(${producto.id})">Añadir</button>
                </div>
            `;
            productosGrid.appendChild(card);
        });
    } catch (error) {
        console.error("Error al cargar la vitrina:", error);
    }
}

// --- AGREGAR AL CARRITO (POST) ---
async function agregarAlCarrito(productoId) {
    try {
        const bodyReq = JSON.stringify({
            productoId: productoId,
            cantidad: 1 // Por defecto añade 1 como en el Figma
        });

        await fetchWithHttpErrorInterceptor(`${API_BASE_URL}/carrito/items`, {
            method: 'POST',
            headers: getHeaders(),
            body: bodyReq
        });

        // Refrescar la vista del carrito tras añadir
        cargarCarrito();
    } catch (error) {
        console.error("Error al agregar ítem:", error);
    }
}

// --- RENDERIZADO DEL CARRITO ---
async function cargarCarrito() {
    try {
        const response = await fetchWithHttpErrorInterceptor(`${API_BASE_URL}/carrito`, {
            method: 'GET',
            headers: getHeaders()
        });

        const carrito = await response.json();
        actualizarUI(carrito);
    } catch (error) {
        // Si es 404 (carrito no existe aún), mostramos vacío
        actualizarUI(null);
    }
}

function actualizarUI(carrito) {
    const itemsContainer = document.getElementById('cart-items');
    const subtotalEl = document.getElementById('cart-subtotal');
    const totalEl = document.getElementById('cart-total');
    const btnPagar = document.getElementById('btn-pagar');

    itemsContainer.innerHTML = '';

    if (!carrito || !carrito.items || carrito.items.length === 0) {
        itemsContainer.innerHTML = '<p class="empty-cart-msg">Tu carrito está vacío</p>';
        subtotalEl.textContent = '0 COP';
        totalEl.textContent = '0 COP';
        btnPagar.disabled = true;
        return;
    }

    // Renderizar cada ítem del carrito
    carrito.items.forEach(item => {
        // Asegúrate de que tu backend devuelve item.producto.nombre en el JSON de respuesta
        const nombreProducto = item.producto ? item.producto.nombre : 'Producto';

        const itemEl = document.createElement('div');
        itemEl.className = 'cart-item';
        itemEl.innerHTML = `
            <div class="item-info">
                <h5>${nombreProducto}</h5>
                <span>x${item.cantidad}</span>
            </div>
            <div class="item-price">
                ${item.subtotal} COP
            </div>
        `;
        itemsContainer.appendChild(itemEl);
    });

    // Actualizar Totales
    subtotalEl.textContent = `${carrito.total} COP`;
    totalEl.textContent = `${carrito.total} COP`;
    btnPagar.disabled = false;
}

// Simulación del interceptor del equipo por si necesitas probarlo independiente
async function fetchWithHttpErrorInterceptor(url, options) {
    const res = await fetch(url, options);
    if (!res.ok) {
        throw new Error(`Error HTTP: ${res.status}`);
    }
    return res;
}
