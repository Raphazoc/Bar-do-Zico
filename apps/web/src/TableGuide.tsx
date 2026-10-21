import React,{useEffect,useState} from 'react';
import {SimpleDialog} from './legacy';

declare global {interface Window {barReceiptPrinter?:{preview:(accountId:string)=>void}}}

/** Native PC uses the Windows driver. The guide remains in memory. */
export function TableGuideButton({accountId,disabled=false,label='Imprimir guia da mesa',inline=false}:{accountId:string;disabled?:boolean;label?:string;inline?:boolean}){
  const [open,setOpen]=useState(false),[url,setUrl]=useState(''),[error,setError]=useState('');
  useEffect(()=>{
    if(!open)return;
    let live=true,objectUrl='';const controller=new AbortController();setError('');setUrl('');
    fetch('/api/accounts/'+encodeURIComponent(accountId)+'/guide.pdf',{credentials:'same-origin',cache:'no-store',signal:controller.signal})
      .then(async response=>{if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.error||'Não foi possível preparar a guia. Tente novamente.');}if(!response.headers.get('content-type')?.startsWith('application/pdf'))throw new Error('A resposta não contém a guia. Reconecte o programa.');return response.blob();})
      .then(blob=>{if(live){objectUrl=URL.createObjectURL(blob);setUrl(objectUrl);}}).catch(failure=>{if(live)setError(failure.message);});
    return()=>{live=false;controller.abort();if(objectUrl)URL.revokeObjectURL(objectUrl);};
  },[open,accountId]);
  function preview(){if(window.barReceiptPrinter){window.barReceiptPrinter.preview(accountId);return;}setOpen(true);}
  const content=<><p>Confira os produtos e o total com o cliente. Visualizar ou imprimir não registra pagamento nem altera o atendimento.</p>{error?<p className="error" role="alert">{error}</p>:!url?<p role="status">Preparando a guia…</p>:<><iframe className="guide-preview" title="Prévia da guia da mesa" src={url}/><div className="guide-actions"><a className="guide-button" href={url} target="_blank" rel="noopener">Imprimir</a></div><p>No navegador, use o botão de impressão do documento aberto. Escolha a impressora de 80 mm e o tamanho real.</p></>}<button className="secondary" data-dialog-cancel onClick={()=>setOpen(false)}>Fechar guia</button></>;
  return <><button className="secondary" disabled={disabled} onClick={preview}>{label}</button>{open&&(inline?<section className="guide-inline" aria-label="Guia da mesa"><h3>Guia da mesa · 80 mm</h3>{content}</section>:<SimpleDialog title="Guia da mesa · 80 mm" onClose={()=>setOpen(false)}>{content}</SimpleDialog>)}</>;
}
