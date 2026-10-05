# language: es
@self-healing
Característica: Self-healing de locators con IA
  Como equipo de QA
  Quiero que la automatización tolere cambios cosméticos del front
  Para que un refactor de clases CSS no rompa pruebas cuyo flujo funcional sigue intacto

  @ui-cambiada
  Escenario: El flujo de búsqueda sigue funcionando tras un refactor de clases CSS del front
    Dado que el usuario está en el home de Guzmán Corretaje
    Cuando selecciona la operación "Comprar"
    Y filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    Y presiona Buscar
    Entonces ve el listado "Propiedades en Venta" con al menos 1 resultado
    Cuando abre el detalle de la primera propiedad del listado
    Entonces el detalle muestra el mismo título, ubicación, precio y código de la propiedad seleccionada

  # Escenario de demostración: se espera que FALLE, porque el botón Buscar deja de estar disponible
  # y eso sí afecta el flujo; el self-healing no debe "repararlo" y el triage debe diagnosticarlo.
  # El nombre es neutral a propósito: el agente de triage no debe deducir la causa por el título.
  # Se excluye de la ejecución normal (@manual); para correrlo:
  #   mvn test -Dcucumber.filter.tags=@ui-rota
  @ui-rota @manual
  Escenario: Buscar departamentos en venta en Ñuñoa desde el buscador del home
    Dado que el usuario está en el home de Guzmán Corretaje
    Cuando selecciona la operación "Comprar"
    Y filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    Y presiona Buscar
    Entonces ve el listado "Propiedades en Venta" con al menos 1 resultado
