package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.ProductoDto;
import com.Monarca.Backend.dto.ProductoResponseDto;
import com.Monarca.Backend.dto.VarianteProductoResponseDto;
import com.Monarca.Backend.dto.VarianteProductoInputDto;

import com.Monarca.Backend.model.Categoria;
import com.Monarca.Backend.model.ImagenProducto;
import com.Monarca.Backend.model.Producto;
import com.Monarca.Backend.model.VarianteProducto;

import com.Monarca.Backend.repository.CategoriaRepository;
import com.Monarca.Backend.repository.ImagenProductoRepository;
import com.Monarca.Backend.repository.ProductoRepository;
import com.Monarca.Backend.repository.VarianteProductoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;


@Service
public class ProductoService {


    // =========================================================
    // REPOSITORIOS
    // =========================================================

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private VarianteProductoRepository varianteRepository;

    @Autowired
    private ImagenProductoRepository imagenRepository;


    // =========================================================
    // LISTAR PRODUCTOS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listarTodos() {

        return productoRepository.findAll();
    }


    // =========================================================
    // BUSCAR PRODUCTO POR ID
    // =========================================================

    @Transactional(readOnly = true)
    public Optional<Producto> buscarPorId(Long id) {

        return productoRepository.findById(id);
    }




    // =========================================================
    // GUARDAR PRODUCTO
    // =========================================================

    @Transactional
    public Producto guardar(ProductoDto dto) {
        validarDto(dto);
        validarImagen(dto.getImg());
        if (dto.getPrecioBase() != null && dto.getPrecioBase().signum() < 0) {
            throw new IllegalArgumentException("Precio base inválido");
        }

        // Validar todas las variantes antes de guardar; la transacción es atómica.
        List<VarianteProductoInputDto> entradas = prepararVariantes(dto);
        Categoria categoria = categoriaRepository.findByNombreIgnoreCase(dto.getCategoria())
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + dto.getCategoria()));

        Producto producto = new Producto();
        producto.setNombre(dto.getNombre().trim());
        producto.setCategoria(categoria);
        producto.setSlug(generarSlugUnico(dto.getNombre(), null));
        producto.setDescripcion(valorODefecto(dto.getDescripcion(), "Sin descripción"));
        producto.setMarca(valorODefecto(dto.getMarca(), "Monarca"));
        producto.setPrecioBase(dto.getPrecioBase() != null
                ? dto.getPrecioBase() : entradas.get(0).getPrecio());
        producto.setActivo(true);
        producto.setDestacado(false);
        producto = productoRepository.save(producto);

        for (VarianteProductoInputDto entrada : entradas) {
            VarianteProducto variante = new VarianteProducto();
            variante.setProducto(producto);
            variante.setSku(generarSku(producto));
            variante.setTalla(entrada.getTalla());
            variante.setColor(entrada.getColor());
            variante.setColorHex(entrada.getColorHex());
            variante.setPrecio(entrada.getPrecio());
            variante.setStock(entrada.getStock());
            variante.setStockMinimo(entrada.getStockMinimo());
            variante.setActivo(true);
            varianteRepository.save(variante);
        }

        if (dto.getImg() != null && !dto.getImg().isBlank()) {
            ImagenProducto imagen = new ImagenProducto();
            imagen.setProducto(producto);
            imagen.setVariante(null);
            imagen.setUrl(dto.getImg().trim());
            imagen.setTextoAlternativo(producto.getNombre());
            imagen.setPrincipal(true);
            imagen.setOrden(0);
            imagenRepository.save(imagen);
        }
        return producto;
    }

    // =========================================================
    // ACTUALIZAR PRODUCTO
    // =========================================================

    @Transactional
    public Producto actualizar(
            Long id,
            ProductoDto dto
    ) {

        validarImagen(dto.getImg());
        if (dto.getPrecioBase() != null && dto.getPrecioBase().signum() < 0) throw new IllegalArgumentException("Precio base inválido");
        if (dto.getPrecio() != null && dto.getPrecio().signum() < 0) throw new IllegalArgumentException("Precio inválido");
        Producto producto = productoRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Producto no encontrado"
                        )
                );


        // -----------------------------------------------------
        // NOMBRE
        // -----------------------------------------------------

        if (
                dto.getNombre() != null
                        && !dto.getNombre().isBlank()
        ) {

            producto.setNombre(
                    dto.getNombre().trim()
            );


            producto.setSlug(
                    generarSlugUnico(
                            dto.getNombre(),
                            producto.getIdProducto()
                    )
            );
        }


        // -----------------------------------------------------
        // CATEGORÍA
        // -----------------------------------------------------

        if (
                dto.getCategoria() != null
                        && !dto.getCategoria().isBlank()
        ) {

            Categoria categoria =
                    categoriaRepository
                            .findByNombreIgnoreCase(
                                    dto.getCategoria()
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Categoría no encontrada: "
                                                    + dto.getCategoria()
                                    )
                            );


            producto.setCategoria(
                    categoria
            );
        }


        // -----------------------------------------------------
        // DESCRIPCIÓN
        // -----------------------------------------------------

        if (dto.getDescripcion() != null) {

            producto.setDescripcion(
                    dto.getDescripcion()
            );
        }


        // -----------------------------------------------------
        // MARCA
        // -----------------------------------------------------

        if (
                dto.getMarca() != null
                        && !dto.getMarca().isBlank()
        ) {

            producto.setMarca(
                    dto.getMarca().trim()
            );
        }


        // -----------------------------------------------------
        // PRECIO BASE
        // -----------------------------------------------------

        if (dto.getPrecioBase() != null) {
            producto.setPrecioBase(dto.getPrecioBase());
        }


        producto = productoRepository.save(
                producto
        );


        // =====================================================
        // ACTUALIZAR VARIANTE
        // =====================================================

        List<VarianteProducto> variantes =
                varianteRepository
                        .findByProducto_IdProducto(
                                producto.getIdProducto()
                        );


        VarianteProducto variante;


        // -----------------------------------------------------
        // NO EXISTEN VARIANTES
        // -----------------------------------------------------

        if (dto.getIdVariante() != null) {
            variante = varianteRepository.findByIdForUpdate(dto.getIdVariante())
                    .filter(v -> v.getProducto().getIdProducto().equals(id))
                    .orElseThrow(() -> new IllegalArgumentException("La variante no pertenece al producto"));
        }
        else if (variantes.isEmpty()) {

            variante =
                    new VarianteProducto();


            variante.setProducto(
                    producto
            );


            variante.setSku(
                    generarSku(producto)
            );


            variante.setActivo(
                    true
            );


        }

        // -----------------------------------------------------
        // EXISTE SOLO UNA VARIANTE
        // -----------------------------------------------------

        else if (variantes.size() == 1) {

            variante =
                    varianteRepository.findByIdForUpdate(variantes.get(0).getIdVariante()).orElseThrow();

        }

        // -----------------------------------------------------
        // EXISTEN VARIAS VARIANTES
        // -----------------------------------------------------

        else {

            variante =
                    buscarVarianteCoincidente(
                            variantes,
                            dto
                    );
            variante = varianteRepository.findByIdForUpdate(variante.getIdVariante()).orElseThrow();
        }


        // -----------------------------------------------------
        // TALLA
        // -----------------------------------------------------

        if (
                dto.getTalla() != null
                        && !dto.getTalla().isBlank()
        ) {

            variante.setTalla(
                    dto.getTalla().trim()
            );

        } else if (
                variante.getTalla() == null
        ) {

            variante.setTalla(
                    "ÚNICA"
            );
        }


        // -----------------------------------------------------
        // COLOR
        // -----------------------------------------------------

        if (
                dto.getColor() != null
                        && !dto.getColor().isBlank()
        ) {

            variante.setColor(
                    dto.getColor().trim()
            );

        } else if (
                variante.getColor() == null
        ) {

            variante.setColor(
                    "N/A"
            );
        }


        // -----------------------------------------------------
        // COLOR HEX
        // -----------------------------------------------------

        if (dto.getColorHex() != null) {

            variante.setColorHex(
                    dto.getColorHex()
            );
        }


        // -----------------------------------------------------
        // PRECIO DE VARIANTE
        // -----------------------------------------------------

        if (dto.getPrecio() != null) {

            variante.setPrecio(
                    dto.getPrecio()
            );

        } else if (
                variante.getPrecio() == null
        ) {

            variante.setPrecio(
                    producto.getPrecioBase()
            );
        }


        // -----------------------------------------------------
        // STOCK
        // -----------------------------------------------------

        if (dto.getStock() != null) {

            if (dto.getStock() < 0) {

                throw new RuntimeException(
                        "El stock no puede ser negativo"
                );
            }


            variante.setStock(
                    dto.getStock()
            );

        } else if (
                variante.getStock() == null
        ) {

            variante.setStock(
                    0
            );
        }


        // -----------------------------------------------------
        // STOCK MÍNIMO
        // -----------------------------------------------------

        if (dto.getStockMinimo() != null) {

            if (dto.getStockMinimo() < 0) {

                throw new RuntimeException(
                        "El stock mínimo no puede ser negativo"
                );
            }


            variante.setStockMinimo(
                    dto.getStockMinimo()
            );

        } else if (
                variante.getStockMinimo() == null
        ) {

            variante.setStockMinimo(
                    0
            );
        }


        varianteRepository.save(
                variante
        );


        // =====================================================
        // ACTUALIZAR IMAGEN PRINCIPAL
        // =====================================================

        if (
                dto.getImg() != null
                        && !dto.getImg().isBlank()
        ) {

            ImagenProducto imagen =
                    imagenRepository
                            .findFirstByProducto_IdProductoAndPrincipalTrue(
                                    producto.getIdProducto()
                            )
                            .orElseGet(
                                    ImagenProducto::new
                            );


            imagen.setProducto(
                    producto
            );


            /*
             * Imagen principal general del producto.
             */
            imagen.setVariante(
                    null
            );


            imagen.setUrl(
                    dto.getImg().trim()
            );


            imagen.setTextoAlternativo(
                    producto.getNombre()
            );


            imagen.setPrincipal(
                    true
            );


            imagen.setOrden(
                    0
            );


            imagenRepository.save(
                    imagen
            );
        }


        return producto;
    }


    // =========================================================
    // COMPATIBILIDAD TEMPORAL
    // =========================================================



    // =========================================================
    // ELIMINAR PRODUCTO
    // =========================================================

    @Transactional
    public void eliminar(
            Long id
    ) {

        if (
                !productoRepository.existsById(id)
        ) {

            throw new RuntimeException(
                    "Producto no encontrado"
            );
        }


        Producto producto = productoRepository.findById(id).orElseThrow();
        producto.setActivo(false);
        productoRepository.save(producto);
    }




    // =========================================================
    // CATÁLOGO PARA EL FRONTEND
    // =========================================================

    /*
     * Este método NO devuelve directamente las entidades JPA.
     *
     * Devuelve DTOs preparados para el frontend.
     *
     * Así evitamos:
     * - errores de serialización
     * - proxies LAZY de Hibernate
     * - relaciones innecesarias
     * - ciclos JSON
     */
    @Transactional(readOnly = true)
    public List<ProductoResponseDto> listarCatalogo() {
        return productoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .map(this::convertirRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductoResponseDto> buscarCatalogoPorId(Long id) {
        return productoRepository.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .map(this::convertirRespuesta);
    }

    private ProductoResponseDto convertirRespuesta(Producto producto) {
        List<VarianteProductoResponseDto> variantes = varianteRepository
                .findByProducto_IdProductoAndActivoTrue(producto.getIdProducto()).stream()
                .map(v -> new VarianteProductoResponseDto(v.getIdVariante(), v.getSku(),
                        v.getTalla(), v.getColor(), v.getColorHex(), v.getPrecio(), v.getStock()))
                .toList();
        String imagen = imagenRepository.findFirstByProducto_IdProductoAndPrincipalTrue(producto.getIdProducto())
                .map(ImagenProducto::getUrl).orElse(null);
        return new ProductoResponseDto(producto.getIdProducto(), producto.getNombre(), producto.getSlug(),
                producto.getDescripcion(), producto.getMarca(), producto.getPrecioBase(), producto.getDestacado(),
                producto.getCategoria() == null ? null : producto.getCategoria().getNombre(), imagen, variantes);
    }

    // =========================================================
    // BUSCAR VARIANTE
    // =========================================================

    private VarianteProducto buscarVarianteCoincidente(
            List<VarianteProducto> variantes,
            ProductoDto dto
    ) {

        if (
                dto.getTalla() == null
                        || dto.getColor() == null
        ) {

            throw new RuntimeException(
                    "El producto tiene varias variantes. "
                            + "Debes indicar talla y color."
            );
        }


        return variantes
                .stream()

                .filter(variante ->

                        variante.getTalla() != null

                                && variante.getColor() != null

                                && variante
                                .getTalla()
                                .equalsIgnoreCase(
                                        dto.getTalla()
                                )

                                && variante
                                .getColor()
                                .equalsIgnoreCase(
                                        dto.getColor()
                                )
                )

                .findFirst()

                .orElseThrow(() ->
                        new RuntimeException(
                                "No se encontró la variante indicada"
                        )
                );
    }


    // =========================================================
    // VALIDAR DTO
    // =========================================================

    private void validarDto(
            ProductoDto dto
    ) {

        if (dto == null) {

            throw new RuntimeException(
                    "Datos del producto requeridos"
            );
        }


        // -----------------------------------------------------
        // NOMBRE
        // -----------------------------------------------------

        if (
                dto.getNombre() == null
                        || dto.getNombre().isBlank()
        ) {

            throw new RuntimeException(
                    "El nombre es obligatorio"
            );
        }


        // -----------------------------------------------------
        // CATEGORÍA
        // -----------------------------------------------------

        if (
                dto.getCategoria() == null
                        || dto.getCategoria().isBlank()
        ) {

            throw new RuntimeException(
                    "La categoría es obligatoria"
            );
        }


        // -----------------------------------------------------
        // PRECIO
        // -----------------------------------------------------

        if (dto.getVariantes() == null && (dto.getPrecio() == null
                || dto.getPrecio().compareTo(BigDecimal.ZERO) < 0)) {

            throw new RuntimeException(
                    "El precio no es válido"
            );
        }


        // -----------------------------------------------------
        // STOCK
        // -----------------------------------------------------

        if (
                dto.getStock() != null
                        && dto.getStock() < 0
        ) {

            throw new RuntimeException(
                    "El stock no puede ser negativo"
            );
        }


        // -----------------------------------------------------
        // STOCK MÍNIMO
        // -----------------------------------------------------

        if (
                dto.getStockMinimo() != null
                        && dto.getStockMinimo() < 0
        ) {

            throw new RuntimeException(
                    "El stock mínimo no puede ser negativo"
            );
        }
    }


    /** Valida tallas y colores repetidos y conserva el alta antigua de una variante. */
    private List<VarianteProductoInputDto> prepararVariantes(ProductoDto dto) {
        boolean multiple = dto.getVariantes() != null;
        List<VarianteProductoInputDto> entradas;
        if (multiple) {
            entradas = dto.getVariantes();
        } else {
            VarianteProductoInputDto unica = new VarianteProductoInputDto();
            unica.setTalla(dto.getTalla());
            unica.setColor(dto.getColor());
            unica.setColorHex(dto.getColorHex());
            unica.setPrecio(dto.getPrecio());
            unica.setStock(dto.getStock());
            unica.setStockMinimo(dto.getStockMinimo());
            entradas = List.of(unica);
        }
        if (entradas.isEmpty() || entradas.size() > 40) {
            throw new IllegalArgumentException("Registra entre 1 y 40 variantes.");
        }
        Set<String> combinaciones = new HashSet<>();
        for (VarianteProductoInputDto entrada : entradas) {
            if (entrada == null) throw new IllegalArgumentException("No puede haber variantes vacías.");
            if (multiple && (entrada.getTalla() == null || entrada.getTalla().isBlank()
                    || entrada.getColor() == null || entrada.getColor().isBlank())) {
                throw new IllegalArgumentException("Indica talla y color en todas las variantes.");
            }
            String talla = valorODefecto(entrada.getTalla(), "ÚNICA");
            String color = valorODefecto(entrada.getColor(), "N/A");
            if (talla.length() > 20 || color.length() > 50) {
                throw new IllegalArgumentException("La talla o el color exceden la longitud permitida.");
            }
            String clave = talla.toLowerCase(Locale.ROOT) + "|" + color.toLowerCase(Locale.ROOT);
            if (!combinaciones.add(clave)) {
                throw new IllegalArgumentException("No repitas la combinación de talla y color: " + talla + " / " + color);
            }
            if (entrada.getPrecio() == null || entrada.getPrecio().signum() < 0) {
                throw new IllegalArgumentException("Cada variante necesita un precio válido.");
            }
            if ((entrada.getStock() != null && entrada.getStock() < 0)
                    || (entrada.getStockMinimo() != null && entrada.getStockMinimo() < 0)) {
                throw new IllegalArgumentException("El stock y el stock mínimo no pueden ser negativos.");
            }
            entrada.setTalla(talla);
            entrada.setColor(color);
            entrada.setColorHex(entrada.getColorHex() == null ? null : entrada.getColorHex().trim());
            entrada.setStock(entrada.getStock() == null ? 0 : entrada.getStock());
            entrada.setStockMinimo(entrada.getStockMinimo() == null ? 0 : entrada.getStockMinimo());
        }
        return entradas;
    }

    // =========================================================
    // GENERAR SKU
    // =========================================================

    private String generarSku(
            Producto producto
    ) {

        return "MON-" + producto.getIdProducto() + "-" + UUID.randomUUID();
    }


    // =========================================================
    // GENERAR SLUG ÚNICO
    // =========================================================

    private String generarSlugUnico(
            String nombre,
            Long idActual
    ) {

        String base =
                Normalizer
                        .normalize(
                                nombre,
                                Normalizer.Form.NFD
                        )

                        .replaceAll(
                                "\\p{M}",
                                ""
                        )

                        .toLowerCase(
                                Locale.ROOT
                        )

                        .replaceAll(
                                "[^a-z0-9]+",
                                "-"
                        )

                        .replaceAll(
                                "^-|-$",
                                ""
                        );


        if (base.isBlank()) {

            base =
                    "producto";
        }


        String slug =
                base;

        int contador =
                2;


        while (
                idActual == null
                        ? productoRepository
                        .existsBySlug(
                                slug
                        )

                        : productoRepository
                        .existsBySlugAndIdProductoNot(
                                slug,
                                idActual
                        )
        ) {

            slug =
                    base
                            + "-"
                            + contador;

            contador++;
        }


        return slug;
    }


    // =========================================================
    // VALOR POR DEFECTO
    // =========================================================

    private String valorODefecto(
            String valor,
            String defecto
    ) {

        if (
                valor == null
                        || valor.isBlank()
        ) {

            return defecto;
        }


        return valor.trim();
    }
    private void validarImagen(String imagen) {
        if (imagen == null || imagen.isBlank()) return;
        try {
            java.net.URI uri = java.net.URI.create(imagen.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("La imagen debe ser una URL HTTPS válida");
        }
    }

}