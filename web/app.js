const list=document.querySelector('#tasks');
const status=document.querySelector('#status');
const form=document.querySelector('#create');
async function request(url,options){const r=await fetch(url,options);const data=await r.json();if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);return data;}
function paint(tasks){list.replaceChildren();for(const t of tasks){const li=document.createElement('li');const name=document.createElement('span');name.textContent=t.title;if(t.done)name.className='done';const btn=document.createElement('button');btn.textContent=t.done?'Reopen':'Complete';btn.addEventListener('click',async()=>{try{paint(await request(`/api/tasks/${t.id}/toggle`,{method:'POST'}));status.textContent='Task updated';}catch(e){status.textContent=e.message;}});li.append(name,btn);list.append(li);}}
form.addEventListener('submit',async e=>{e.preventDefault();try{const body=new URLSearchParams(new FormData(form));paint(await request('/api/tasks',{method:'POST',body}));form.reset();status.textContent='Task added';}catch(e){status.textContent=e.message;}});
request('/api/tasks').then(paint).catch(e=>status.textContent=e.message);
