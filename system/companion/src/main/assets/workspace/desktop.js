import RFB from './vendor/novnc/core/rfb.js';

// noVNC accepts a binary channel. Java carries it over Strata's authenticated
// connection; neither WebView nor the VNC server opens a public network port.
export class Desktop {
  constructor(element, report) {
    this.channel = {
      readyState:0, protocol:'binary', binaryType:'arraybuffer', bufferedAmount:0,
      onopen:null, onmessage:null, onclose:null, onerror:null,
      send(bytes) {
        bytes = new Uint8Array(bytes);
        for (let i=0; i<bytes.length; i+=8192) Strata.input(JSON.stringify({op:'input',
          data:btoa(String.fromCharCode(...bytes.subarray(i,i+8192)))}));
      },
      close() { this.readyState=3; Strata.detach(); this.onclose?.({wasClean:true}); }
    };
    this.rfb = new RFB(element,this.channel,{shared:true});
    this.rfb.scaleViewport = true;
    this.rfb.resizeSession = true;
    this.rfb.qualityLevel = 6;
    this.rfb.compressionLevel = 2;
    this.rfb.addEventListener('connect',()=>report('Graphical session connected'));
    this.rfb.addEventListener('disconnect',event=>report(event.detail.clean ? 'Desktop disconnected' : 'Desktop connection ended. Reconnect or read the service log.'));
    this.rfb.addEventListener('securityfailure',event=>report(event.detail.reason || 'Display authentication failed'));
  }
  open() { this.channel.readyState=1; this.channel.onopen?.(); }
  data(value) { const bytes=Uint8Array.from(atob(value), c=>c.charCodeAt(0)); this.channel.onmessage?.({data:bytes.buffer}); }
  text(value) { for (const char of value) { const point=char.codePointAt(0); this.rfb.sendKey(point>255 ? 0x01000000 | point : point); } }
  key(value) { this.rfb.sendKey(value); }
  close() { this.rfb.disconnect(); }
}
