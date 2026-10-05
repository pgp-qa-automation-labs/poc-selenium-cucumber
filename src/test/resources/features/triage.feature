# language: es
@triage
Característica: Triage de fallos con IA
  Como equipo de QA
  Quiero que cada escenario fallido llegue con un diagnóstico de su causa probable
  Para saber rápido si es un bug, un problema del ambiente o de la automatización

  # Escenario de demostración: se espera que FALLE. El navegador bloquea las llamadas del front a la API
  # de propiedades, como si el backend estuviera caído. El triage debería identificar el problema de backend.
  #   mvn test -Dcucumber.filter.tags=@api-caida
  @api-caida @manual
  Escenario: El triage identifica un backend caído cuando el listado viene vacío
    Dado que el usuario está en el home de Guzmán Corretaje
    Cuando selecciona la operación "Comprar"
    Y filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    Y presiona Buscar
    Entonces ve el listado "Propiedades en Venta" con al menos 1 resultado
