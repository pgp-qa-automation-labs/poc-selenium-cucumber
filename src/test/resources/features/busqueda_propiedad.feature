# language: es
@busqueda
Característica: Búsqueda de propiedades
  Como cliente de Guzmán Corretaje
  Quiero buscar propiedades según mis filtros
  Para revisar el detalle de las que me interesan

  @smoke
  Escenario: Buscar un departamento en venta en Ñuñoa y revisar su detalle
    Dado que el usuario está en el home de Guzmán Corretaje
    Cuando selecciona la operación "Comprar"
    Y filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    Y presiona Buscar
    Entonces ve el listado "Propiedades en Venta" con al menos 1 resultado
    Y todas las propiedades del listado están en la comuna "Ñuñoa"
    Cuando abre el detalle de la primera propiedad del listado
    Entonces el detalle muestra el mismo título, ubicación, precio y código de la propiedad seleccionada
    Y el detalle muestra las características "Dormitorios" y "Baños"
    Y el detalle muestra la conversión a pesos según el valor de la UF del día
