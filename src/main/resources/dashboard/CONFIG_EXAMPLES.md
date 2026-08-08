# Dashboard Configuration Examples

## api_config.yml

```yaml
port: 8080
enable_cors: true
rate_limit:
  enabled: true
  requests_per_minute: 60
log_requests: true
log_errors: true
api_keys:
  read_only: []
```

## OBS Browser Source

Add `http://<server-ip>:8080/dashboard/live-map.html` as a Browser Source in OBS.
