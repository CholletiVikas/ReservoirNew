document.addEventListener('DOMContentLoaded', function() {
    loadReservoirs();
    document.getElementById('reservoirForm').addEventListener('submit', handleSubmit);
});

async function loadReservoirs() {
    try {
        const response = await fetch('/api/reservoirs');
        const reservoirs = await response.json();
        
        const select = document.getElementById('reservoirName');
        reservoirs.forEach(reservoir => {
            const option = document.createElement('option');
            option.value = reservoir.name;
            option.textContent = reservoir.name;
            select.appendChild(option);
        });
    } catch (error) {
        console.error('Error loading reservoirs:', error);
        showMessage('Error loading reservoir list', 'error');
    }
}

async function handleSubmit(event) {
    event.preventDefault();
    
    const reservoirName = document.getElementById('reservoirName').value;
    const waterLevel = document.getElementById('waterLevel').value;
    const inflow = document.getElementById('inflow').value;
    const outflow = document.getElementById('outflow').value;
    const waterStorage = document.getElementById('waterStorage').value;

    if (!reservoirName) {
        showMessage('Please select a reservoir', 'error');
        return;
    }

    const submission = {
        reservoirName,
        waterLevel,
        inflow,
        outflow,
        waterStorage,
        submissionDate: new Date().toISOString().split('T')[0]
    };
    
    const button = document.querySelector('.btn-submit');
    button.disabled = true;
    
    try {
        const response = await fetch('/api/submit', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(submission)
        });
        
        const result = await response.json();
        
        if (result.status === 'success') {
            showMessage('Data submitted successfully!', 'success');
            document.getElementById('reservoirForm').reset();
        } else {
            showMessage(result.message || 'Submission failed', 'error');
        }
    } catch (error) {
        console.error('Error submitting form:', error);
        showMessage('Error submitting data. Please try again.', 'error');
    } finally {
        button.disabled = false;
    }
}

function showMessage(message, type) {
    const messageDiv = document.getElementById('message');
    messageDiv.textContent = message;
    messageDiv.className = 'message ' + type;
    
    setTimeout(() => {
        messageDiv.className = 'message';
    }, 5000);
}
