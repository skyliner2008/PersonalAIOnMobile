param(
  [string]$Symbol = 'OANDA:XAUUSD',
  [string]$Resolution = '60',
  [int]$Bars = 300,
  [int]$TimeoutSec = 12
)

function Wrap([string]$p){ return "~m~$($p.Length)~m~$p" }

function Parse-TvFrames([string]$buffer){
  $messages = New-Object System.Collections.Generic.List[string]
  $pos = 0
  while ($true) {
    if ($pos -ge $buffer.Length) { break }

    if (-not $buffer.Substring($pos).StartsWith('~m~')) {
      $next = $buffer.IndexOf('~m~', $pos)
      if ($next -lt 0) {
        return @{ Messages = $messages; Remainder = $buffer.Substring($pos) }
      }
      $pos = $next
    }

    $lenStart = $pos + 3
    $lenEnd = $buffer.IndexOf('~m~', $lenStart)
    if ($lenEnd -lt 0) { break }

    $lenText = $buffer.Substring($lenStart, $lenEnd - $lenStart)
    $len = 0
    if (-not [int]::TryParse($lenText, [ref]$len)) {
      $pos = $lenEnd + 3
      continue
    }

    $msgStart = $lenEnd + 3
    $msgEnd = $msgStart + $len
    if ($msgEnd -gt $buffer.Length) { break }

    $messages.Add($buffer.Substring($msgStart, $len))
    $pos = $msgEnd
  }

  return @{ Messages = $messages; Remainder = $buffer.Substring($pos) }
}

function Get-FromParam([string]$s){
  $san = $s.Replace(':', '-')
  return "symbols/$san/"
}

$uri = [Uri]("wss://data.tradingview.com/socket.io/websocket?from={0}" -f [uri]::EscapeDataString((Get-FromParam $Symbol)))
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ws.Options.SetRequestHeader('Origin', 'https://www.tradingview.com')
$ct = [Threading.CancellationToken]::None

$chartSession = "cs_test_{0}" -f ([Guid]::NewGuid().ToString('N').Substring(0,8))
$symbolAlias = 'symbol_1'
$seriesName = 's1'

function Send-WS([System.Net.WebSockets.ClientWebSocket]$w, [string]$text, $token){
  $bytes = [Text.Encoding]::UTF8.GetBytes($text)
  $seg = [ArraySegment[byte]]::new($bytes)
  $w.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $token).GetAwaiter().GetResult() | Out-Null
}

function Send-Cmd([System.Net.WebSockets.ClientWebSocket]$w, [string]$method, [object[]]$params, $token){
  $payload = @{ m = $method; p = $params } | ConvertTo-Json -Compress -Depth 10
  Send-WS $w (Wrap $payload) $token
}

try {
  $ws.ConnectAsync($uri, $ct).GetAwaiter().GetResult() | Out-Null

  Send-Cmd $ws 'set_data_quality' @('low') $ct
  Send-Cmd $ws 'set_auth_token' @('unauthorized_user_token') $ct
  Send-Cmd $ws 'chart_create_session' @($chartSession, '') $ct

  $resolveObj = @{ symbol = $Symbol; adjustment = 'splits'; session = 'regular' } | ConvertTo-Json -Compress
  $resolveStr = '=' + $resolveObj
  Send-Cmd $ws 'resolve_symbol' @($chartSession, $symbolAlias, $resolveStr) $ct
  Send-Cmd $ws 'create_series' @($chartSession, $seriesName, $seriesName, $symbolAlias, $Resolution, $Bars) $ct
  Send-Cmd $ws 'switch_timezone' @($chartSession, 'Etc/UTC') $ct

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  $buffer = ''
  $count = 0
  $firstTs = $null
  $lastTs = $null
  $methodsSeen = New-Object System.Collections.Generic.HashSet[string]

  while ((Get-Date) -lt $deadline) {
    $buf = New-Object byte[] 131072
    $seg = [ArraySegment[byte]]::new($buf)
    $task = $ws.ReceiveAsync($seg, $ct)
    if (-not $task.Wait(1200)) { continue }
    $res = $task.Result
    if ($res.Count -le 0) { continue }

    $chunk = [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    $buffer += $chunk

    $parsed = Parse-TvFrames $buffer
    $buffer = $parsed.Remainder

    foreach ($packet in $parsed.Messages) {
      if ($packet.StartsWith('~h~')) { Send-WS $ws (Wrap $packet) $ct; continue }

      try { $obj = $packet | ConvertFrom-Json -ErrorAction Stop } catch { continue }
      $m = [string]$obj.m
      if ($m) { [void]$methodsSeen.Add($m) }
      if ($m -ne 'timescale_update') { continue }

      $payload = $obj.p
      if (-not $payload -or $payload.Count -lt 2) { continue }
      $body = $payload[1]
      if ($body -is [string]) {
        try { $body = $body | ConvertFrom-Json -ErrorAction Stop } catch { continue }
      }

      $series = $body.$seriesName
      if (-not $series) { continue }
      $barsArr = $series.s
      if (-not $barsArr) { continue }
      if ($barsArr -is [string]) { continue }

      $count = @($barsArr).Count
      if ($count -gt 0) {
        $firstTs = [long]$barsArr[0].v[0]
        $lastTs = [long]$barsArr[$count - 1].v[0]
        break
      }
    }

    if ($count -gt 0) { break }
  }

  [pscustomobject]@{
    symbol = $Symbol
    resolution = $Resolution
    bars_requested = $Bars
    bars_received = $count
    first_ts = $firstTs
    last_ts = $lastTs
    methods_seen = ($methodsSeen | Sort-Object) -join ','
    ok = ($count -gt 0)
  } | ConvertTo-Json -Compress
}
finally {
  try { $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'bye', $ct).GetAwaiter().GetResult() | Out-Null } catch {}
  $ws.Dispose()
}
