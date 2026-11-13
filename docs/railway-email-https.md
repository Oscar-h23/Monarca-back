# Correos en Railway Hobby mediante Resend HTTPS

Railway deshabilita SMTP saliente en Hobby, aunque funcione en local.
Los errores se muestran como MailSendException por timeout al puerto 587.

## Producción Railway

Establecer `MAIL_TRANSPORT=resend-http` en el servicio MONARCA-BACK.
`ResendHttpsMailSender` sustituye a SMTP y usa
`POST https://api.resend.com/emails` (HTTPS, puerto 443).
No se agregan nuevos servicios ni claves. El backend reutiliza:
- `MAIL_PASSWORD`: la API key existente de Resend (solo en Railway)
- `MAIL_FROM`: remitente con dominio verificado
- `MAIL_HOST` y `MAIL_USERNAME`: se mantienen para compatibilidad de las validaciones anteriores
- `MAIL_ADMIN_TO` (opcional): destinatario administrativo; por defecto, ADMIN activos de MonarcaDB
- `SHOP_PUBLIC_URL` y `PASSWORD_RESET_URL`: enlaces a la tienda

La recuperación de contraseñas y las notificaciones de pedidos emplean
el mismo JavaMailSender, por lo que ambos pasan al transporte HTTPS.

## Desarrollo local

No establecer MAIL_TRANSPORT mantiene el SMTP previo y las pruebas locales.

## Validación

1. Reiniciar backend y verificar estado SUCCESS.
2. Panel Admin > Resumen > Notificaciones: enviar correo de prueba.
3. Confirmar en Resend que se generó y que el destinatario lo recibió.
4. Crear pedido de prueba, adjuntar comprobante y cambiar estado.
5. Comprobar correos de comprador y ADMIN, y recuperación de contraseña.

La confirmación de una transacción no debe depender del envío de correo.
Los eventos pasados que fallaron no se reenvían automáticamente.

Referencia: https://docs.railway.com/networking/outbound-networking
