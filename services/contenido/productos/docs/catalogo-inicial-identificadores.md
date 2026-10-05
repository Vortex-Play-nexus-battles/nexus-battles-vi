# Catalogo inicial: identificadores

Identificador de cada producto que siembra `SemillaDelCatalogo` (ver la seccion "Catalogo inicial" del [README](../README.md)). El id es un UUID version 3 derivado del slug del JSON: `UUID.nameUUIDFromBytes("nexus-battles-vi/catalogo-inicial/" + slug)`. Es el mismo en cualquier maquina y en cualquier corrida, asi que inventario puede guardarlo como `productoId` con confianza.

Esta tabla se genera a partir de `src/main/resources/semilla/catalogo-inicial.json`; `SemillaDelCatalogoTest` falla si alguna fila no coincide con la derivacion.

| Slug | Id (UUID) | Tipo | Nombre |
|---|---|---|---|
| `heroe-guerrero-tanque` | `aec4fbd2-9615-352a-9f2b-3fad781e123c` | HEROE | Guerrero Tanque |
| `heroe-guerrero-armas` | `2239ecfa-3fc4-3f02-9d87-df52cf06665b` | HEROE | Guerrero Armas |
| `heroe-mago-fuego` | `f6176d50-5ce8-3f27-8ca0-c7382a11512b` | HEROE | Mago Fuego |
| `heroe-mago-hielo` | `81213f7d-fa7b-3bcc-aa41-5f67af771a20` | HEROE | Mago Hielo |
| `heroe-picaro-veneno` | `52ea4bcd-8f8d-351a-a713-0ec7b5470611` | HEROE | Pícaro Veneno |
| `heroe-picaro-machete` | `8b924e5d-b90f-3178-a7f5-0b2ab6238a74` | HEROE | Pícaro Machete |
| `heroe-chaman` | `2aa4cd6b-8580-31c7-8790-435a5d8a2332` | HEROE | Chamán |
| `heroe-medico` | `5d8c4c8c-0fb3-3c55-bbc0-4039a5dc1fd0` | HEROE | Médico |
| `arma-guerrero-tanque-espada-de-una-mano` | `1647b2ea-096d-37e7-b580-0172e4c62313` | ARMA | Espada de una mano |
| `arma-guerrero-armas-espada-de-dos-manos` | `37154b84-2ea0-3076-ae7b-656f7b4ebf11` | ARMA | Espada de dos manos |
| `arma-guerrero-tanque-escudo-de-dragon` | `f7639c96-5fdd-38e1-b474-b4cc7123c499` | ARMA | Escudo de dragón |
| `arma-guerrero-armas-piedra-de-afilar` | `bcccae4f-a74b-30b2-b89b-5d1882b609d5` | ARMA | Piedra de afilar |
| `arma-mago-fuego-orbe-de-manos-ardientes` | `77d90598-f504-33cb-9b68-080f0c89493d` | ARMA | Orbe de manos ardientes |
| `arma-mago-hielo-baculo-de-permafrost` | `cc0a77c9-0358-39b6-8558-d19e062adfed` | ARMA | Báculo de Permafrost |
| `arma-mago-fuego-fuego-fatuo` | `4c7472fe-fa32-33b6-bc1c-9b1154affa43` | ARMA | Fuego fatuo |
| `arma-mago-hielo-venas-heladas` | `a43732c6-c357-379c-ad39-b3529e5e5ade` | ARMA | Venas heladas |
| `arma-picaro-veneno-daga-purulenta` | `d7d1836f-e45e-33f4-a033-6e467017f1df` | ARMA | Daga purulenta |
| `arma-picaro-machete-machete-vendito` | `2666f981-47b8-3d75-9b84-df92bd32a1c0` | ARMA | Machete vendito |
| `arma-picaro-veneno-vision-borrosa` | `3c27a62b-7276-37b4-a7c9-4c6355683b6f` | ARMA | Visión borrosa |
| `arma-picaro-machete-cierra-sangrienta` | `192e50d8-274a-360b-b3b4-6bd3dc4e27ff` | ARMA | Cierra sangrienta |
| `arma-chaman-raiz-china` | `94f85da1-0004-369f-bb43-1ff7ca966b84` | ARMA | Raíz china |
| `arma-medico-kit-de-urgencias` | `69a631af-491a-3264-8684-cd01d20cef60` | ARMA | Kit de urgencias |
| `arma-chaman-yerbabuena` | `a0c8771c-b54b-3a07-a8aa-dcc769500d7e` | ARMA | Yerbabuena |
| `arma-medico-reanimador` | `8b88964f-b8d1-33a1-929d-e36e13ed9218` | ARMA | Reanimador |
| `armadura-guerrero-tanque-defensa-del-enfurecido` | `fbce687b-7496-3526-aef0-500b3fe2135e` | ARMADURA | Defensa del enfurecido |
| `armadura-guerrero-armas-puno-lucido` | `ab5febe7-a919-3c66-895a-965362820dde` | ARMADURA | Puño lúcido |
| `armadura-guerrero-tanque-magma-ardiente` | `70f08e92-b6e0-350b-8e5e-695e4a92180b` | ARMADURA | Magma Ardiente |
| `armadura-guerrero-armas-punos-en-llamas` | `1d402ac5-f98b-3d21-b5da-ff81913dea29` | ARMADURA | Puños en llamas |
| `armadura-mago-fuego-tunica-arcana` | `75c8d169-edac-3e08-b038-85cb6bb9068e` | ARMADURA | Túnica arcana |
| `armadura-mago-hielo-corona-de-hielo` | `fa5a4825-585e-3b01-9daf-7869f7fa202f` | ARMADURA | Corona de hielo |
| `armadura-mago-fuego-caida-de-fuego` | `23679400-559b-38da-950c-68a800634fe3` | ARMADURA | Caída de fuego |
| `armadura-mago-hielo-ventisca` | `824c1e0c-6c2d-3e12-b62e-345e1338e649` | ARMADURA | Ventisca |
| `armadura-picaro-veneno-mano-del-desterrado` | `14413b85-7d68-3e83-a258-9044147440d6` | ARMADURA | Mano del desterrado |
| `armadura-picaro-machete-pie-de-atleta` | `32ef0eb6-e4a9-39c2-850c-83fe1d3922bd` | ARMADURA | Pie de atleta |
| `armadura-picaro-veneno-atadura-carmesi` | `1336666f-1c41-3511-a7da-13d21ded606d` | ARMADURA | Atadura carmesí |
| `armadura-picaro-machete-sangre-cruel` | `9f0bfe7a-ad41-36ca-9d05-b44589dd99e0` | ARMADURA | Sangre cruel |
| `armadura-chaman-piel-de-caminante-del-bosque` | `8e7996ae-5579-3087-bffa-25bbcb7870e1` | ARMADURA | Piel de Caminante del Bosque |
| `armadura-medico-bata-de-cirujano` | `b4a0aece-aaf0-3b9a-98b8-59504bf505ed` | ARMADURA | Bata de Cirujano |
| `armadura-chaman-casco-de-ecos-ancestrales` | `0a1cab7c-87ac-3f17-8540-143f37a595da` | ARMADURA | Casco de Ecos Ancestrales |
| `armadura-medico-pantalon-de-expedicion-medica` | `20d98991-33cc-3f11-8b72-a04ebe9d5c4f` | ARMADURA | Pantalón de Expedición Médica |
| `item-guerrero-tanque-pinchos-de-escudo` | `7cd7b72c-912f-3abd-9724-8a271b7fc77d` | ITEM | Pinchos de escudo |
| `item-guerrero-armas-empunadura-de-furia` | `ad5a6a53-b398-3741-93c8-59efe4f6578f` | ITEM | Empuñadura de Furia |
| `item-mago-fuego-anillo-para-piro-explosion` | `d7369838-a2a4-3810-9dd6-5b6c0ba97089` | ITEM | Anillo para Piro-explosión |
| `item-mago-hielo-libro-de-la-ventisca-helada` | `57ac6dfa-568b-3a89-87ab-4853dfdb0135` | ITEM | Libro de la ventisca helada |
| `item-picaro-veneno-veneno-lacerante` | `ae461eab-cdbd-370d-8241-99f875417657` | ITEM | Veneno lacerante |
| `item-picaro-machete-mancuerna-yugular` | `5c52954a-131b-372c-a3c7-713e114e902f` | ITEM | Mancuerna yugular |
| `item-chaman-pluma-sanadora` | `70e6ad6c-ff34-30ab-a57c-be975c17bffb` | ITEM | Pluma sanadora |
| `item-medico-benditas` | `52d9af58-8cc1-3ba4-9e59-573cb7b69e79` | ITEM | Benditas |
| `epica-guerrero-tanque-golpe-de-defensa` | `81af272d-74fb-3dc1-b6ff-01fdc99a1c1d` | EPICA | Golpe de defensa |
| `epica-guerrero-armas-segundo-impulso` | `4481eb34-384a-3fa0-ba9a-1aac9562c38f` | EPICA | Segundo impulso |
| `epica-mago-fuego-luz-cegadora` | `5a969684-9f1b-3dcd-b1e7-1b407aef8c09` | EPICA | Luz cegadora |
| `epica-mago-hielo-frio-concentrado` | `978446ae-c979-349b-b620-f4215852ab5f` | EPICA | Frio concentrado |
| `epica-picaro-veneno-toma-y-lleva` | `ac841a0a-132a-392f-8cf5-5a9cfa9add0f` | EPICA | Toma y lleva |
| `epica-picaro-machete-intimidacion-sangrienta` | `cd15a03a-57db-3159-a709-9632140db557` | EPICA | Intimidación sangrienta |
| `epica-chaman-te-changua` | `0e616a1a-9298-3f7c-bac2-c5dcf601424a` | EPICA | Té changua |
| `epica-medico-reanimador-3000` | `005fb6b8-eab8-3f7f-879b-dbe7e3e6e439` | EPICA | Reanimador 3000 |
