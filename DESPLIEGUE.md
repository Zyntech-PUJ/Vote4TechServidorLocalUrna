# Guía de despliegue — Vote4Tech Servidor Local de Urna

## Arquitectura general

```
Tablets Android (Urna)
        ↓ Wi-Fi LAN
ServidorLocalUrna (Docker, tu máquina)
    ├── PostgreSQL local  :5432  — datos electorales descargados
    └── CouchDB local     :5984  — votos anónimos
        ↕ Sync manual (botones en http://localhost:8081)
VM central (10.43.98.254) vía túneles SSH
    ├── PostgreSQL central :5434  — BD nacional con candidatos, ciudadanos, elecciones
    └── CouchDB central    :5985  — repositorio global de votos
```

---

## Prerrequisitos (solo la primera vez)

### 1. Clave SSH y autostart de túneles

La clave SSH ya está generada en `~/.ssh/vote4tech_vm`.  
El script `~/.ssh/start-vm-tunnels.ps1` se ejecuta automáticamente al iniciar sesión en Windows.

Si necesitas regenerar la clave o reregistrar el autostart:

```powershell
# Generar clave (si no existe)
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\vote4tech_vm" -N "" -C "vote4tech-tunnel"

# Copiar la clave pública a la VM (requiere la contraseña una vez)
# Pegar el contenido de vote4tech_vm.pub en ~/.ssh/authorized_keys de la VM

# Registrar autostart
$script = "$env:USERPROFILE\.ssh\start-vm-tunnels.ps1"
$cmd = "powershell.exe -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" `
    -Name "Vote4Tech-VM-Tunnels" -Value $cmd -Type String
```

### 2. CouchDB en la VM central

Ya está instalado (`couchdb-central`, `restart: unless-stopped`). Se levanta solo con la VM.  
Bases de datos presentes: `votos_urna`, `votos_domicilio`.

---

## Arranque normal (cada sesión)

### Paso 1 — Verificar que los túneles SSH estén activos

```powershell
netstat -ano | findstr "15434"
netstat -ano | findstr "15985"
```

Deben aparecer en estado `LISTENING`. Si no aparecen, ejecutar manualmente:

```bash
# En Git Bash o terminal
ssh -i ~/.ssh/vote4tech_vm \
    -o StrictHostKeyChecking=no \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -N \
    -L 0.0.0.0:15434:localhost:5434 \
    -L 0.0.0.0:15985:localhost:5985 \
    estudiante@10.43.98.254 &
```

> Los túneles se levantan automáticamente al iniciar sesión en Windows mediante el autostart registrado en `HKCU\...\Run`. Solo tienes que ejecutarlos manualmente si acabas de registrar la sesión y no han aparecido aún.

### Paso 2 — Levantar el ServidorLocalUrna

```bash
cd "Vote4TechServidorLocalUrna"
docker compose up -d --build
```

Servicios que levanta:
| Servicio | Puerto externo | Descripción |
|---|---|---|
| `backend` | 8081 | API REST para las tablets + UI de administración |
| `postgres` | 5432 | PostgreSQL local con datos electorales |
| `couchdb` | 5984 | CouchDB local con votos de urna |

Panel de administración web: **http://localhost:8081**

### Paso 3 — Descargar datos electorales desde la VM

En el panel web (`http://localhost:8081`), presionar **"Descargar datos electorales"**.

Esto descarga desde la VM (`10.43.98.254`):
- Partidos, elecciones, candidatos, mesas, centros, ciudadanos desde PostgreSQL (:5434)
- Votos ya subidos al central desde CouchDB (:5985)

### Paso 4 — Instalar la app de urna en las tablets

La app Android (`Vote4TechVotacionUrna`) debe estar compilada e instalada.  
Configurar la URL del servidor en la app: `http://<IP_LOCAL_DE_TU_MÁQUINA>:8081`

> La tablet y el servidor deben estar en la misma red Wi-Fi.

---

## Flujo de votación

1. El ciudadano ingresa su cédula en la tablet → se verifica contra PostgreSQL local
2. Pasa por la pantalla de verificación biométrica (mock)
3. Selecciona elección → vota → el voto se guarda en **CouchDB local** (anónimo)
4. En `ya_voto` (PostgreSQL local) queda registrado que esa cédula votó en esa elección

---

## Sincronización con la VM central

En el panel web (`http://localhost:8081`):

| Botón | Qué hace |
|---|---|
| **Descargar datos electorales** | PostgreSQL VM → PostgreSQL local (candidatos, ciudadanos, etc.) + CouchDB VM → CouchDB local (votos de otros servidores) |
| **Subir votos al central** | CouchDB local → CouchDB VM (solo votos no subidos aún) |

### Detección de conflictos

Antes de subir o descargar, el sistema compara los UUIDs de votos en local y en central:
- Si hay votos duplicados (mismo UUID), el sistema espera 6 segundos mostrando una advertencia y luego **elimina el duplicado local** (el central tiene prioridad) y descarga la versión del central al local.

---

## Pila central (para pruebas integradas locales)

Si no hay conexión a la VM o se quiere probar todo localmente:

```bash
# Desde la raíz del workspace (carpeta Arquitectura de Software)
docker compose -f docker-compose.central-local.yml up -d --build
```

Luego cambiar en `Vote4TechServidorLocalUrna/docker-compose.yml`:
```yaml
CENTRAL_DB_URL: jdbc:postgresql://host.docker.internal:5433/bd_nacional_vote4tech
CENTRAL_COUCHDB_URL: http://host.docker.internal:5985
```

Revertir los valores originales (`15434` / `15985`) cuando vuelvas a usar la VM.

---

## Credenciales

| Sistema | Usuario | Contraseña |
|---|---|---|
| VM (`10.43.98.254`) | `estudiante` | `ArquiSoftVote4Tech` |
| PostgreSQL local / central | `admin` / `admin_db_nacional` | `admin123` / `12345` |
| CouchDB local | `admin` | `admin123` |
| CouchDB central (VM) | `admin` | `admin123` |
| App urna — registrador | `admin` | `admin123` |
| App urna — jurado | cédula `10001` | `jurado123` |
| Ciudadano de prueba (urna) | cédula `12345678` | — |

---

## Troubleshooting

### Los túneles no están activos
```bash
# Verificar
netstat -ano | findstr "15434"

# Levantar manualmente (Git Bash)
ssh -i ~/.ssh/vote4tech_vm -o StrictHostKeyChecking=no -N \
  -L 0.0.0.0:15434:localhost:5434 \
  -L 0.0.0.0:15985:localhost:5985 \
  estudiante@10.43.98.254 &
```

### "Sin conexión al sistema central" al descargar
1. Verificar que los túneles estén activos (paso anterior)
2. Verificar que la VM esté encendida: `ping 10.43.98.254`
3. Verificar que CouchDB esté corriendo en la VM:
   ```bash
   curl http://localhost:15985/
   ```

### "Sin conexión al sistema central" al subir votos
Mismo diagnóstico que arriba, pero también verificar que `votos_urna` existe en el CouchDB de la VM:
```bash
curl -u admin:admin123 http://localhost:15985/votos_urna
```

### Los votos no aparecen en las tablets después de reiniciar el servidor
Presionar "Descargar datos electorales" — los votos del central se restauran al local automáticamente.

### Reconectar el túnel si la VM cambia de IP o llave SSH
```bash
# Eliminar la llave antigua y regenerar
rm ~/.ssh/vote4tech_vm ~/.ssh/vote4tech_vm.pub
ssh-keygen -t ed25519 -f ~/.ssh/vote4tech_vm -N "" -C "vote4tech-tunnel"
# Luego copiar vote4tech_vm.pub al authorized_keys de la VM
```
