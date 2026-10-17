# FormulaRacing Dashboard - Configuration Examples

## api_config.yml

```yaml
port: 10003
enable_cors: true
rate_limit:
  enabled: true
  requests_per_minute: 60
log_requests: true
log_errors: true
auth:
  enabled: false
  token: ""
  tokens: []
  protect_logs: false
```

## Notes
- The dashboard directory is created automatically on first startup.
- Bundled resources are copied when the directory does not already exist.
- Edit `api_config.yml` and restart to apply changes.
