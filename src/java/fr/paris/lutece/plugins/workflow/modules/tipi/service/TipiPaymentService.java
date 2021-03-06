/*
 * Copyright (c) 2002-2018, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.workflow.modules.tipi.service;

import java.util.List;

import javax.inject.Inject;

import fr.paris.lutece.plugins.workflow.modules.tipi.business.Tipi;
import fr.paris.lutece.plugins.workflow.modules.tipi.business.TipiRefDetHistory;
import fr.paris.lutece.plugins.workflow.modules.tipi.business.TipiRefDetIdOpHistory;
import fr.paris.lutece.plugins.workflow.modules.tipi.business.TipiTransactionResult;
import fr.paris.lutece.plugins.workflow.modules.tipi.business.task.TaskTipiConfig;
import fr.paris.lutece.plugins.workflow.modules.tipi.business.task.TaskTipiConfigDAO;
import fr.paris.lutece.plugins.workflow.modules.tipi.exception.TipiNotFoundException;
import fr.paris.lutece.plugins.workflow.modules.tipi.exception.TransactionResultException;
import fr.paris.lutece.plugins.workflow.modules.tipi.util.TipiConstants;
import fr.paris.vdp.tipi.create.url.enumeration.TransactionResult;

/**
 * This class represents a service for the TIPI payment
 *
 */
public class TipiPaymentService implements ITipiPaymentService
{
    private final ITipiService _tipiService;
    private final ITipiRefDetHistoryService _tipiRefDetHistoryService;
    private final ITipiWorkflowStateService _tipiWorkflowStateService;
    private final ITipiServiceCaller _tipiServiceCaller;
    private final TaskTipiConfigDAO _taskTipiConfigDAO;
    private final ITipiRefDetIdOpHistoryService _tipiRefDetIdOpHistoryService;

    /**
     * Constructor
     * 
     * @param tipiService
     *            the TIPI service
     * @param tipiRefDetHistoryService
     *            the RefDetHistory service
     * @param tipiWorkflowStateService
     *            the workflow state service
     * @param tipiServiceCaller
     *            the TIPI service caller
     */
    @Inject
    public TipiPaymentService( ITipiService tipiService, ITipiRefDetHistoryService tipiRefDetHistoryService,
            ITipiWorkflowStateService tipiWorkflowStateService, ITipiServiceCaller tipiServiceCaller,ITipiRefDetIdOpHistoryService tipiRefDetIdOpHistoryService )
    {
        _tipiService = tipiService;
        _tipiRefDetHistoryService = tipiRefDetHistoryService;
        _tipiWorkflowStateService = tipiWorkflowStateService;
        _tipiServiceCaller = tipiServiceCaller;
        _taskTipiConfigDAO = new TaskTipiConfigDAO( );
        _tipiRefDetIdOpHistoryService=tipiRefDetIdOpHistoryService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void paymentProcessed( Tipi tipi ) throws TransactionResultException
    {
        TipiTransactionResult transactionResult = findTransactionResult( tipi.getIdOp( ) );
        paymentProcessed( tipi, transactionResult );
    }
    
    

    /**
     * {@inheritDoc}
     */
    @Override
    public void paymentProcessed( String strIdop ) throws TransactionResultException, TipiNotFoundException
    {
        
        TipiTransactionResult transactionResult = findTransactionResult( strIdop );
        //Multiple idop can be generated for the same refDet
        //Get the RefDet in the transactionResult
        Tipi tipi = findTipiByRefDet( transactionResult.getRefDet( ) );
        tipi.setIdOp( transactionResult.getIdop( ) );
        
        paymentProcessed( tipi,transactionResult );
    }
    
   
    private void paymentProcessed(Tipi tipi,TipiTransactionResult transactionResult) throws TransactionResultException
    {
        TipiRefDetHistory refDetHistory = findRefDetHistory( tipi );
        List<TipiRefDetIdOpHistory> listRefDetIdopHistory=_tipiRefDetIdOpHistoryService.getByRefDet( tipi.getRefDet( ) );
        //update transaction the payment is succeded or if ther is only ine transaction for a refDet
        if(TransactionResult.PAYMENT_SUCCEEDED.getValueStr( ).equals( transactionResult.getTransactionResult( ))||listRefDetIdopHistory.isEmpty( )|| listRefDetIdopHistory.size( )==1)
        {
            saveTransactionResult( tipi, transactionResult.getTransactionResult( ) );
            changeWorklowState( transactionResult.getTransactionResult( ), refDetHistory );
        }
        _tipiRefDetIdOpHistoryService.removeByIdop( tipi.getIdOp( ) );
    }

    /**
     * Finds the transaction result associated to the specified idop
     * 
     * @param strIdop
     *            the idop
     * @return the transaction result
     * @throws TransactionResultException
     *             if there is an error getting the TIPI transaction result
     */
    private TipiTransactionResult findTransactionResult( String strIdop ) throws TransactionResultException
    {
        return _tipiServiceCaller.getTransactionResult( strIdop );
    }

    /**
     * Finds the TIPI object associated to the specified RefDet
     * 
     * @param strIdop
     *            the idop
     * @return the TIPI object
     * @throws TipiNotFoundException
     *             if there is no TIPI object associated to the specified idop
     */
    private Tipi findTipiByRefDet( String strRefDet ) throws TipiNotFoundException
    {
        Tipi tipi = _tipiService.findByPrimaryKey( strRefDet );

        if ( tipi == null )
        {
            throw new TipiNotFoundException( "There is no TIPI object associated to the RefDet " + strRefDet );
        }

        return tipi;
    }

    /**
     * Finds the {@code RefDetHistory} object associated to the specified TIPI object
     * 
     * @param tipi
     *            the TIPI object
     * @return the {@code RefDetHistory} object
     */
    private TipiRefDetHistory findRefDetHistory( Tipi tipi )
    {
        return _tipiRefDetHistoryService.findByRefDet( tipi.getRefDet( ) );
    }

    /**
     * Saves the specified transaction result for the specified TIPI object
     * 
     * @param tipi
     *            the TIPI object
     * @param strTransactionResult
     *            the transaction result
     */
    private void saveTransactionResult( Tipi tipi, String strTransactionResult )
    {
        tipi.setTransactionResult( strTransactionResult );
        _tipiService.update( tipi );
    }

    /**
     * Changes the workflow state of the workflow resource depending on the specified transaction result
     * 
     * @param strTransactionResult
     *            the transaction result
     * @param refDetHistory
     *            the {@code RefDetHistory} object used to retrieve the workflow resource
     */
    private void changeWorklowState( String strTransactionResult, TipiRefDetHistory refDetHistory )
    {
        
        
        
        
        TaskTipiConfig taskTipiConfig = _taskTipiConfigDAO.load( refDetHistory.getIdTask( ) );
        int nIdResourceHistory = refDetHistory.getIdHistory( );

        if ( TransactionResult.PAYMENT_SUCCEEDED.getValueStr( ).equals( strTransactionResult ) )
        {
            _tipiWorkflowStateService.changeState( taskTipiConfig.getIdStateForProcessingStateModif(), taskTipiConfig.getIdStateAfterSuccessPayment( ), nIdResourceHistory );
        }

        if ( TransactionResult.PAYMENT_FAILED.getValueStr( ).equals( strTransactionResult ) )
        {
            _tipiWorkflowStateService.changeState( taskTipiConfig.getIdStateForProcessingStateModif(), taskTipiConfig.getIdStateAfterFailurePayment( ), nIdResourceHistory );
        }

        if ( TipiConstants.TRANSACTION_RESULT_PAYMENT_CANCELED.equals( strTransactionResult ) )
        {
            _tipiWorkflowStateService.changeState( taskTipiConfig.getIdStateForProcessingStateModif(), taskTipiConfig.getIdStateAfterCanceledPayment( ), nIdResourceHistory );
        }
    }

}
